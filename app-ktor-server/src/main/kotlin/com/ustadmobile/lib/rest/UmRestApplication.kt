package com.ustadmobile.lib.rest

import com.google.gson.Gson
import com.ustadmobile.core.account.*
import com.ustadmobile.core.contentformats.epub.EpubContentImporterCommonJvm
import com.ustadmobile.core.contentformats.epub.XhtmlFixer
import com.ustadmobile.core.contentformats.h5p.H5PContentImportPlugin
import com.ustadmobile.core.contentformats.media.VideoContentImporterJvm
import com.ustadmobile.core.contentformats.pdf.PdfContentImporterJvm
import com.ustadmobile.core.contentformats.xapi.XapiZipContentImporter
import com.ustadmobile.core.contentjob.ContentJobManager
import com.ustadmobile.core.contentjob.ContentJobManagerJvm
import com.ustadmobile.core.contentjob.ContentImportersManager
import com.ustadmobile.core.db.UmAppDatabase
import com.ustadmobile.core.db.UmAppDatabase_KtorRoute
import com.ustadmobile.core.impl.di.CommonJvmDiModule
import com.ustadmobile.core.util.DiTag
import com.ustadmobile.core.util.DiTag.TAG_CONTEXT_DATA_ROOT
import com.ustadmobile.door.*
import com.ustadmobile.door.ext.*
import com.ustadmobile.core.impl.*
import com.ustadmobile.core.io.UploadSessionManager
import com.ustadmobile.lib.rest.ext.*
import com.ustadmobile.lib.rest.messaging.MailProperties
import com.ustadmobile.lib.util.ext.bindDataSourceIfNotExisting
import com.ustadmobile.lib.util.sanitizeDbNameFromUrl
import io.github.aakira.napier.Napier
import io.ktor.server.application.*
import io.ktor.serialization.gson.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import org.kodein.di.*
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory
import java.io.File
import java.nio.file.Files
import javax.naming.InitialContext
import com.ustadmobile.door.util.NodeIdAuthCache
import com.ustadmobile.core.impl.config.SupportedLanguagesConfig
import com.ustadmobile.core.impl.di.DomainDiModuleJvm
import com.ustadmobile.core.impl.locale.StringProvider
import com.ustadmobile.core.impl.locale.StringProviderJvm
import com.ustadmobile.core.schedule.initQuartzDb
import com.ustadmobile.core.uri.UriHelper
import com.ustadmobile.core.uri.UriHelperJvm
import com.ustadmobile.door.http.DoorHttpServerConfig
import com.ustadmobile.lib.rest.dimodules.makeJvmBackendDiModule
import com.ustadmobile.lib.rest.ffmpeghelper.InvalidFffmpegException
import com.ustadmobile.lib.rest.ffmpeghelper.NoFfmpegException
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import com.ustadmobile.lib.util.SysPathUtil
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.websocket.*
import org.kodein.di.ktor.di
import java.util.*
import com.ustadmobile.lib.rest.logging.LogbackAntiLog
import com.ustadmobile.libcache.headers.FileMimeTypeHelperImpl
import com.ustadmobile.libcache.UstadCache
import com.ustadmobile.libcache.UstadCacheBuilder
import kotlinx.io.files.Path
import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFprobe
import nl.adaptivity.xmlutil.serialization.XML
import org.kodein.di.ktor.closestDI
import java.net.Inet6Address
import java.net.NetworkInterface

const val TAG_UPLOAD_DIR = 10

@Suppress("unused")
const val CONF_DBMODE_VIRTUALHOST = "virtualhost"

const val CONF_DBMODE_SINGLETON = "singleton"

const val CONF_GOOGLE_API = "secret"

const val CONF_KEY_SITE_URL = "ktor.ustad.siteUrl"

/**
 * List of external commands (e.g. media converters) that must be found or have locations specified
 */
val REQUIRED_EXTERNAL_COMMANDS = emptyList<String>()

/**
 * List of prefixes which are always answered by the KTOR server. When using JsDev proxy mode, any
 * other url will be sent to the JS dev proxy
 */
val KTOR_SERVER_ROUTES = listOf(
    "/UmAppDatabase", "/ConcatenatedContainerFiles2",
    "/ContainerEntryList", "/ContainerEntryFile", "/auth", "/ContainerMount",
    "/ContainerUpload2", "/Site", "/import", "/contentupload", "/websocket", "/pdf",
    "/api"
)


/**
 * Returns an identifier that is used as a subdirectory for data storage (e.g. attachments,
 * containers, etc).
 */
fun Endpoint.identifier(
    dbMode: String,
    singletonName: String = CONF_DBMODE_SINGLETON
) = if(dbMode == CONF_DBMODE_SINGLETON) {
    singletonName
}else {
    sanitizeDbNameFromUrl(url)
}

@Suppress("unused") // This is used as the KTOR server main module via application.conf
fun Application.umRestApplication(
    dbModeOverride: String? = null,
) {
    val appConfig = environment.config

    val siteUrl = environment.config.propertyOrNull(CONF_KEY_SITE_URL)?.getString()

    if(siteUrl.isNullOrBlank()) {
        val likelyAddr = NetworkInterface.getNetworkInterfaces().toList().filter {
            !it.isLoopback
        }.flatMap { netInterface ->
            netInterface.inetAddresses.toList().filter { it !is Inet6Address }
        }.firstOrNull()?.let { "http://${it.hostAddress}:${appConfig.port}/"} ?: ""

        throw SiteConfigException("ERROR: Site URL is not set. You MUST specify the site url e.g. $likelyAddr \n" +
                "Please specify using the url parameter in command line e.g. add " +
                "--siteUrl $likelyAddr \n" +
                "to the command you are running or \n" +
                "set this in the config file e.g. uncomment siteUrl and set as siteUrl = \"$likelyAddr\"")
    }

    val appFfmpegDir = ktorAppHomeFfmpegDir()
    val ffmpegFile = SysPathUtil.findCommandInPath(
        commandName = "ffmpeg",
        manuallySpecifiedLocation = appConfig.commandFileProperty("ffmpeg"),
        extraSearchPaths = appFfmpegDir.absolutePath,
    )
    val ffprobeFile = SysPathUtil.findCommandInPath(
        commandName = "ffprobe",
        manuallySpecifiedLocation = appConfig.commandFileProperty("ffprobe"),
        extraSearchPaths = appFfmpegDir.absolutePath
    )

    if(ffmpegFile == null || ffprobeFile == null) {
        throw NoFfmpegException()
    }

    try {
        if(!FFmpeg(ffmpegFile.absolutePath).isFFmpeg || !FFprobe(ffprobeFile.absolutePath).isFFprobe) {
            throw InvalidFffmpegException(ffmpegFile, ffprobeFile)
        }
    }catch(e: Exception) {
        //If an exception occurs running them, it is also invalid
        throw InvalidFffmpegException(ffmpegFile, ffprobeFile)
    }

    val devMode = environment.config.propertyOrNull("ktor.ustad.devmode")?.getString().toBoolean()

    val json = Json {
        encodeDefaults = true
    }

    //Check for required external commands
    REQUIRED_EXTERNAL_COMMANDS.forEach { command ->
        if(!SysPathUtil.commandExists(command,
                manuallySpecifiedLocation = appConfig.commandFileProperty(command))
        ) {
            val message = "FATAL ERROR: Required external command \"$command\" not found in path or " +
                   "manually specified location does not exist. Please set it in application.conf"
            Napier.e(message)
            throw IllegalStateException(message)
        }
    }


    if (devMode) {
        install(CORS) {
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Options)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.AccessControlAllowOrigin)
            allowHeader("X-nid")
            allowHeader("door-dbversion")
            allowHeader("door-node")
            anyHost()
        }
    }

    //Uncomment if needed -generates a lot of output
    //install(CallLogging)

    Napier.takeLogarithm()
    Napier.base(LogbackAntiLog())

    install(ContentNegotiation) {
        gson {
            register(ContentType.Application.Json, GsonConverter())
            register(ContentType.Any, GsonConverter())
        }
    }

    //Avoid sending the body of content if it has not changed since the client last requested it.
    install(ConditionalHeaders)

    val tmpRootDir = Files.createTempDirectory("upload").toFile()

    val dbMode = dbModeOverride ?:
        appConfig.propertyOrNull("ktor.ustad.dbmode")?.getString() ?: CONF_DBMODE_SINGLETON

    val dataDirPath = environment.config.absoluteDataDir()

    fun String.replaceDbUrlVars(): String {
        return replace("(datadir)", dataDirPath.absolutePath)
    }

    dataDirPath.takeIf { !it.exists() }?.mkdirs()

    val apiKey = environment.config.propertyOrNull("ktor.ustad.googleApiKey")?.getString() ?: CONF_GOOGLE_API

    di {
        import(CommonJvmDiModule)
        import(DomainDiModuleJvm(EndpointScope.Default))
        import(makeJvmBackendDiModule(environment.config))

        bind<SupportedLanguagesConfig>() with singleton {
            SupportedLanguagesConfig(
                systemLocales = listOf(Locale.getDefault().language),
                settings = instance(),

            )
        }
        bind<StringProvider>() with singleton { StringProviderJvm(Locale.getDefault()) }

        bind<File>(tag = TAG_UPLOAD_DIR) with scoped(EndpointScope.Default).singleton {
            File(tmpRootDir, context.identifier(dbMode)).also {
                it.takeIf { !it.exists() }?.mkdirs()
            }
        }

        bind<ContainerStorageManager>() with scoped(EndpointScope.Default).singleton {
            ContainerStorageManager(listOf(instance<File>(tag = TAG_CONTEXT_DATA_ROOT)))
        }

        bind<NodeIdAuthCache>() with scoped(EndpointScope.Default).singleton {
            instance<UmAppDatabase>(tag = DoorTag.TAG_DB).nodeIdAuthCache
        }

        bind<File>(tag = DiTag.TAG_DEFAULT_CONTAINER_DIR) with scoped(EndpointScope.Default).singleton {
            val containerDir = File(instance<File>(tag = TAG_CONTEXT_DATA_ROOT), "container")

            //Move any old container directory to the new path (e.g. pre database v57)
            if(context == Endpoint("localhost")){
                val oldContainerDir = File("build/storage/singleton/container")
                if(oldContainerDir.exists() && !oldContainerDir.renameTo(containerDir)) {
                    throw IllegalStateException("Old singleton container dir present but cannot " +
                            "rename from ${oldContainerDir.absolutePath} to ${containerDir.absolutePath}")
                }
            }

            containerDir.takeIf { !it.exists() }?.mkdirs()
            containerDir
        }

        bind<String>(tag = DiTag.TAG_GOOGLE_API) with singleton {
            apiKey
        }

        bind<Gson>() with singleton { Gson() }

        bind<UstadCache>() with singleton {
            val dbUrl = "jdbc:sqlite:(datadir)/ustadcache.db"
                .replace("(datadir)", appConfig.absoluteDataDir().absolutePath)
            UstadCacheBuilder(
                dbUrl = dbUrl,
                storagePath = Path(
                    File(appConfig.absoluteDataDir(), "httpfiles").absolutePath.toString()
                )
            ).build()
        }

        bind<UriHelper>() with singleton {
            UriHelperJvm(
                mimeTypeHelperImpl = FileMimeTypeHelperImpl(),
                httpClient = instance(),
                okHttpClient = instance(),
            )
        }

        bind<ContentImportersManager>() with scoped(EndpointScope.Default).singleton {
            val cache: UstadCache = instance()
            val uriHelper: UriHelper = instance()
            val xml: XML = instance()
            val xhtmlFixer: XhtmlFixer = instance()

            ContentImportersManager(
                listOf(
                    EpubContentImporterCommonJvm(
                        endpoint = context,
                        di = di,
                        cache = cache,
                        uriHelper = uriHelper,
                        xml = xml,
                        xhtmlFixer = xhtmlFixer,
                    ),
                    XapiZipContentImporter(
                        endpoint = context,
                        di = di,
                        cache = cache,
                        uriHelper = uriHelper
                    ),
                    PdfContentImporterJvm(
                        endpoint = context,
                        di = di,
                        cache= cache,
                        uriHelper = uriHelper,
                    ),
                    H5PContentImportPlugin(
                        endpoint = context,
                        di = di,
                        cache = cache,
                        uriHelper = uriHelper,
                        json = instance(),
                    ),
                    VideoContentImporterJvm(
                        endpoint = context,
                        di = di,
                        cache = cache,
                        uriHelper = uriHelper,
                        ffprobe = instance(),
                        json = instance(),
                    )
                )
            )
        }

        bind<Scheduler>() with singleton {
            val dbProperties = environment.config.databasePropertiesFromSection("quartz",
                "jdbc:sqlite:(datadir)/quartz.sqlite?journal_mode=WAL&synchronous=OFF&busy_timeout=30000")
            dbProperties.setProperty("url", dbProperties.getProperty("url").replaceDbUrlVars())

            InitialContext().apply {
                bindDataSourceIfNotExisting("quartzds", dbProperties)
                initQuartzDb("java:/comp/env/jdbc/quartzds")
            }
            StdSchedulerFactory.getDefaultScheduler().also {
                it.context.put("di", di)
            }
        }

        bind<UploadSessionManager>() with scoped(EndpointScope.Default).singleton {
            UploadSessionManager(context, di)
        }

        bind<ContentJobManager>() with singleton {
            ContentJobManagerJvm(di)
        }

        bind<Json>() with singleton {
            json
        }

        bind<FFmpeg>() with provider {
            FFmpeg(ffmpegFile.absolutePath)
        }

        bind<FFprobe>() with provider {
            FFprobe(ffprobeFile.absolutePath)
        }

        bind<File>(tag = DiTag.TAG_FILE_UPLOAD_TMP_DIR) with scoped(EndpointScope.Default).singleton {
            File(instance<File>(tag = TAG_CONTEXT_DATA_ROOT), UPLOAD_TMP_SUBDIR).also {
                if(!it.exists())
                    it.mkdirs()
            }
        }

        try {
            appConfig.config("mail")

            bind<MailProperties>() with singleton {
                MailProperties(appConfig.property("mail.from").getString(),
                    appConfig.toProperties(MailProperties.SMTP_PROPS))
            }

            bind<NotificationSender>() with singleton {
                NotificationSender(di)
            }

            bind<Authenticator>() with singleton {
                object: Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(
                            appConfig.property("mail.user").getString(),
                            appConfig.property("mail.auth").getString()
                        )
                    }
                }
            }
        }catch(e: Exception) {
            Napier.w("WARNING: Email sending not configured")
        }

        registerContextTranslator { call: ApplicationCall ->
            call.callEndpoint
        }

        onReady {
            if(dbMode == CONF_DBMODE_SINGLETON) {
                //Get the container dir so that any old directories (build/storage etc) are moved if required
                di.on(Endpoint("localhost")).direct.instance<File>(tag = DiTag.TAG_DEFAULT_CONTAINER_DIR)
                //Generate the admin username/password etc.
                di.on(Endpoint("localhost")).direct.instance<AuthManager>()
            }

            instance<Scheduler>().start()
            Runtime.getRuntime().addShutdownHook(Thread{
                instance<Scheduler>().shutdown()
            })
        }
    }

    //Ensure that older clients that make http calls to pages that no longer exist will not make
    // an infinite number of calls and exhaust their data bundle etc.
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { _: HttpStatusCode ->
            call.respondText("Not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
        }
    }

    val jsDevServer = appConfig.propertyOrNull("ktor.ustad.jsDevServer")?.getString()
    if(jsDevServer != null) {
        install(io.ktor.server.websocket.WebSockets)

        intercept(ApplicationCallPipeline.Setup) {
            val requestUri = call.request.uri.let {
                if(it.startsWith("//")) {
                    //This is an edge case with the ContainerFetcher. The ContainerFetcher uses //
                    // at the start of a URI. This workaround will be removed when ContainerFetcher
                    // is removed and replaced with Retriever.
                    it.removePrefix("/")
                }else {
                    it
                }
            }

            //If the request is not using the correct url as per system config, reject it and finish
            if(!context.urlMatchesConfig()) {
                call.respondRequestUrlNotMatchingSiteConfUrl()
                return@intercept finish()
            }

            //If the request is not matching any API route, then use the reverse proxy to send the
            // request to the javascript development server.
            if(!KTOR_SERVER_ROUTES.any { requestUri.startsWith(it) }) {
                call.respondReverseProxy(jsDevServer)
                return@intercept finish()
            }
        }
    }

    /**
     * Note: to facilitate Javascript development, make sure that any route prefixes used are listed
     * in UstadAppReactProxy
     */
    install(Routing) {
        addHostCheckIntercept()

        ContainerDownload()
        personAuthRegisterRoute()
        ContainerMountRoute()
        ContainerUploadRoute2()
        route("UmAppDatabase") {
            UmAppDatabase_KtorRoute(DoorHttpServerConfig(json = json)) { call ->
                val di: DI by call.closestDI()
                di.on(call).direct.instance(tag = DoorTag.TAG_DB)
            }
        }
        SiteRoute()

        ContentUploadRoute()

        GetAppRoute()

        route("api") {
            route("pbkdf2"){
                Pbkdf2Route()
            }

            route("contentupload") {
                ContentUploadRoute()
            }

            route("import") {
                ContentEntryImportRoute()
            }

            val di: DI by closestDI()
            ContentEntryVersionRoute(
                cache = di.direct.instance()
            )
        }

        static("umapp") {
            resources("umapp")
            static("/") {
                defaultResource("umapp/index.html")
            }
        }

        //Handle default route when running behind proxy
        if(!jsDevServer.isNullOrBlank()) {
            webSocketProxyRoute(jsDevServer)
        }else {
            route("/"){
                get{
                    call.respondRedirect("umapp/")
                }
            }
        }
    }

    //Tell anyone looking that the server is up/running and where to find logs
    // As per logback.xml
    val logDir = System.getProperty("logs_dir") ?: "./log/"
    val printableServerUrl = if(dbMode == CONF_DBMODE_VIRTUALHOST) {
        "*:${appConfig.port}"
    }else {
        appConfig.siteUrl()
    }

    println("Ustad server is running on $printableServerUrl . Logging to $logDir .")
    println()
    println("You can connect the Android client to this address as per README.md .")
    println()
    if(jsDevServer != null) {
        println("Javascript development mode is enabled. If you want to use the web client in a browser, you must run: ")
        println("./gradlew app-react:jsRun")
        println("Then open $printableServerUrl in your browser. See app-react/README.md for more details.")
    }else if(this::class.java.getResource("/umapp/index.html") != null) {
        println(" This build includes the web client, you can access it by opening $printableServerUrl in your browser.")
    }else {
        println(" This build does not include the web client and Javascript dev mode is not enabled.")
        println(" If you want to use the web client in a browser, please see app-react/README.md .")
    }
    println()
    println("Use [Ctrl+C] to stop.")
}

