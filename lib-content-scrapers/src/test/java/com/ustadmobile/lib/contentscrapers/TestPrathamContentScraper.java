package com.ustadmobile.lib.contentscrapers;

import com.ustadmobile.core.db.UmAppDatabase;
import com.ustadmobile.lib.contentscrapers.africanbooks.AsbScraper;
import com.ustadmobile.lib.contentscrapers.ddl.DdlContentScraper;
import com.ustadmobile.lib.contentscrapers.ddl.IndexDdlContent;
import com.ustadmobile.lib.contentscrapers.prathambooks.IndexPrathamContentScraper;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;

import static com.ustadmobile.lib.contentscrapers.ScraperConstants.UTF_ENCODING;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class TestPrathamContentScraper {


    final Dispatcher dispatcher = new Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) throws InterruptedException {

            try {

                if (request.getPath().contains("json")) {

                    String fileName = request.getPath().substring(5,
                            request.getPath().length());
                    String body = IOUtils.toString(getClass().getResourceAsStream(fileName), UTF_ENCODING);
                    return new MockResponse().setBody(body);

                } else if (request.getPath().contains("content")) {

                    String fileLocation = request.getPath().substring(8,
                            request.getPath().length());
                    InputStream videoIn = getClass().getResourceAsStream(fileLocation);
                    BufferedSource source = Okio.buffer(Okio.source(videoIn));
                    Buffer buffer = new Buffer();
                    source.readAll(buffer);

                    return new MockResponse().setResponseCode(200).setBody(buffer);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
            return new MockResponse().setResponseCode(404);
        }
    };


    @Test
    public void givenServerOnline_whenVideoContentScraped_thenShouldConvertAndDownload() throws IOException, URISyntaxException {

        File tmpDir = Files.createTempDirectory("testindexPrathamcontentscraper").toFile();

        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);

        IndexPrathamContentScraper scraper = spy(new IndexPrathamContentScraper());
        doReturn(mockWebServer.url("/json/com/ustadmobile/lib/contentscrapers/pratham/prathamonebook.txt").url()).when(scraper).generatePrathamUrl("1");
        doReturn(mockWebServer.url("/json/com/ustadmobile/lib/contentscrapers/pratham/prathamlist.txt").url()).when(scraper).generatePrathamUrl("2");
        doReturn(mockWebServer.url("/json/com/ustadmobile/lib/contentscrapers/pratham/prathamempty.txt").url()).when(scraper).generatePrathamUrl("3");
        doReturn(mockWebServer.url("/content/com/ustadmobile/lib/contentscrapers/pratham/24620-a-book-for-puchku.epub").url()).when(scraper).generatePrathamEPubFileUrl(Mockito.anyString());
        doReturn("").when(scraper).loginPratham();

        scraper.findContent(tmpDir);

        File resourceFolder = new File(tmpDir, "5859");
        Assert.assertEquals(true, resourceFolder.isDirectory());

        File contentFolder = new File(resourceFolder, "content");

        File contentFile = new File(contentFolder, "5859-come-home-papa.epub");
        Assert.assertEquals(true, ContentScraperUtil.fileHasContent(contentFile));


    }

    @Test
    public void testasb() throws IOException {

        UmAppDatabase db = UmAppDatabase.getInstance(null);
        db.clearAllTables();

        File tmpDir = Files.createTempDirectory("testindexAsbcontentscraper").toFile();

        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);

        AsbScraper scraper = spy(new AsbScraper());
        doReturn(mockWebServer.url("/json/com/ustadmobile/lib/contentscrapers/africanbooks/abslist.txt").url()).when(scraper).generateURL();
        doReturn(mockWebServer.url("/content/com/ustadmobile/lib/contentscrapers/africanbooks/asb18187.epub").url()).when(scraper).generateEPubUrl(Mockito.any(), Mockito.anyString());
        doReturn(mockWebServer.url("/json/com/ustadmobile/lib/contentscrapers/africanbooks/abslist.txt").url()).when(scraper).generatePublishUrl(Mockito.any(), Mockito.anyString());
        doReturn(mockWebServer.url("/json/com/ustadmobile/lib/contentscrapers/africanbooks/abslist.txt").url()).when(scraper).generateMakeUrl(Mockito.any(), Mockito.anyString());
        doReturn(mockWebServer.url("/json/com/ustadmobile/lib/contentscrapers/africanbooks/asbreader.txt").url().toString()).when(scraper).generateReaderUrl(Mockito.any(), Mockito.anyString());
        doReturn(mockWebServer.url("/json/com/ustadmobile/lib/contentscrapers/africanbooks/asburl.txt").url().toString()).when(scraper).getAfricanStoryBookUrl();

        scraper.findContent(tmpDir);

        File contentFile = new File(tmpDir, "asb10674.epub");
        Assert.assertEquals(true, ContentScraperUtil.fileHasContent(contentFile));

        db.clearAllTables();

        scraper.findContent(tmpDir);

    }


    @Test
    public void testDdl() throws IOException {

        File tmpDir = Files.createTempDirectory("testindexDdlontentscraper").toFile();

        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(dispatcher);

        DdlContentScraper scraper = new DdlContentScraper(
                mockWebServer.url("json/com/ustadmobile/lib/contentscrapers/ddl/ddlcontent.txt").toString(),
                tmpDir);
        try {
            scraper.scrapeContent();
            scraper.getParentSubjectAreas();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        File contentFolder = new File(tmpDir, "ddlcontent");
        Assert.assertEquals(true, contentFolder.isDirectory());

        File contentFile = new File(contentFolder, "311.pdf");
        Assert.assertEquals(true, ContentScraperUtil.fileHasContent(contentFile));


    }
}