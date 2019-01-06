package com.ustadmobile.lib.contentscrapers.etekkatho;

import com.ustadmobile.core.db.UmAppDatabase;
import com.ustadmobile.core.db.dao.ContentEntryContentEntryFileJoinDao;
import com.ustadmobile.core.db.dao.ContentEntryDao;
import com.ustadmobile.core.db.dao.ContentEntryFileDao;
import com.ustadmobile.core.db.dao.ContentEntryFileStatusDao;
import com.ustadmobile.core.db.dao.ContentEntryParentChildJoinDao;
import com.ustadmobile.core.db.dao.LanguageDao;
import com.ustadmobile.lib.contentscrapers.ContentScraperUtil;
import com.ustadmobile.lib.contentscrapers.LanguageList;
import com.ustadmobile.lib.db.entities.ContentEntry;
import com.ustadmobile.lib.db.entities.Language;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import static com.ustadmobile.lib.contentscrapers.ScraperConstants.EMPTY_STRING;
import static com.ustadmobile.lib.contentscrapers.ScraperConstants.ROOT;
import static com.ustadmobile.lib.contentscrapers.ScraperConstants.USTAD_MOBILE;
import static com.ustadmobile.lib.db.entities.ContentEntry.LICENSE_TYPE_CC_BY;


/**
 *
 *
 */
public class IndexEtekkathoScraper {

    private static final String ETEKKATHO = "Etekkatho";
    private URL url;
    private File destinationDirectory;
    private ContentEntryDao contentEntryDao;
    private ContentEntryParentChildJoinDao contentParentChildJoinDao;
    private ContentEntryFileDao contentEntryFileDao;
    private ContentEntryContentEntryFileJoinDao contentEntryFileJoin;
    private ContentEntryFileStatusDao contentFileStatusDao;
    private LanguageDao languageDao;
    private HashMap<String, ContentEntry> headingHashMap;
    private Language englishLang;
    private int subjectCount = 0;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: <voa html url> <file destination>");
            System.exit(1);
        }

        System.out.println(args[0]);
        System.out.println(args[1]);
        try {
            new IndexEtekkathoScraper().findContent(args[0], new File(args[1]));
        } catch (IOException e) {
            System.err.println("Exception running findContent");
            e.printStackTrace();
        }
    }

    public void findContent(String urlString, File destinationDir) throws IOException {

        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            System.out.println("Index Malformed url" + urlString);
            throw new IllegalArgumentException("Malformed url" + urlString, e);
        }

        destinationDir.mkdirs();
        destinationDirectory = destinationDir;

        UmAppDatabase db = UmAppDatabase.getInstance(null);
        db.setMaster(true);
        UmAppDatabase repository = db.getRepository("https://localhost", "");
        contentEntryDao = repository.getContentEntryDao();
        contentParentChildJoinDao = repository.getContentEntryParentChildJoinDao();
        contentEntryFileDao = repository.getContentEntryFileDao();
        contentEntryFileJoin = repository.getContentEntryContentEntryFileJoinDao();
        contentFileStatusDao = db.getContentEntryFileStatusDao();
        languageDao = repository.getLanguageDao();
        headingHashMap = new HashMap<>();

        new LanguageList().addAllLanguages();

        englishLang = ContentScraperUtil.insertOrUpdateLanguageByName(languageDao, "English");

        ContentEntry masterRootParent = ContentScraperUtil.createOrUpdateContentEntry(ROOT, USTAD_MOBILE,
                ROOT, USTAD_MOBILE, LICENSE_TYPE_CC_BY, englishLang.getLangUid(), null,
                EMPTY_STRING, false, EMPTY_STRING, EMPTY_STRING,
                EMPTY_STRING, EMPTY_STRING, contentEntryDao);

        ContentEntry parentEtek = ContentScraperUtil.createOrUpdateContentEntry("http://www.etekkatho.org/subjects/", "Voice of America - Learning English",
                "http://www.etekkatho.org/", ETEKKATHO, LICENSE_TYPE_CC_BY, englishLang.getLangUid(), null,
                "Educational resources for the Myanmar academic community", false, EMPTY_STRING,
                "http://www.etekkatho.org/img/logos/etekkatho-myanmar-lang.png",
                EMPTY_STRING, EMPTY_STRING, contentEntryDao);

        ContentScraperUtil.insertOrUpdateParentChildJoin(contentParentChildJoinDao, masterRootParent, parentEtek, 7);

        Document document = Jsoup.connect(urlString).get();

        Elements elements = document.select("tr th[scope=row], tr td");

        int subjectCount = 0;
        int headingCount = 0;
        ContentEntry subjectEntry = null;
        for (int i = 0; i < elements.size(); i++) {

            Element element = elements.get(i);

            if (!element.attr("scope").isEmpty()) {

                // found Main Content
                subjectEntry = ContentScraperUtil.createOrUpdateContentEntry(element.text(),
                        element.text(), element.selectFirst("a").attr("href"),
                        ETEKKATHO, LICENSE_TYPE_CC_BY, englishLang.getLangUid(), null,
                        "", false, EMPTY_STRING, EMPTY_STRING,
                        EMPTY_STRING, EMPTY_STRING, contentEntryDao);

                ContentScraperUtil.insertOrUpdateParentChildJoin(contentParentChildJoinDao, parentEtek, subjectEntry, subjectCount++);

                Element subHeadingElement = elements.get(++i);
                Element descriptionElement = elements.get(++i);

                String title = subHeadingElement.text();
                if (title.contains("*")) {
                    title = title.replace("*", "").trim();
                }

                ContentEntry subHeadingEntry = ContentScraperUtil.createOrUpdateContentEntry(subHeadingElement.text(),
                        title, element.text() + "/" + subHeadingElement.text(),
                        ETEKKATHO, LICENSE_TYPE_CC_BY, englishLang.getLangUid(), null,
                        descriptionElement.text(), false, EMPTY_STRING, EMPTY_STRING,
                        EMPTY_STRING, EMPTY_STRING, contentEntryDao);

                headingHashMap.put(title, subHeadingEntry);

                ContentScraperUtil.insertOrUpdateParentChildJoin(contentParentChildJoinDao, subjectEntry, subHeadingEntry, headingCount++);


            } else if (element.hasClass("span3")) {

                Element descriptionElement = elements.get(++i);
                String title = element.text();
                if (title.contains("*")) {
                    title = title.replace("*", "").trim();
                }

                ContentEntry subHeadingEntry = ContentScraperUtil.createOrUpdateContentEntry(element.text(),
                        title, subjectEntry.getTitle() + "/" + element.text(),
                        ETEKKATHO, LICENSE_TYPE_CC_BY, englishLang.getLangUid(), null,
                        descriptionElement.text(), false, EMPTY_STRING, EMPTY_STRING,
                        EMPTY_STRING, EMPTY_STRING, contentEntryDao);

                headingHashMap.put(title, subHeadingEntry);

                ContentScraperUtil.insertOrUpdateParentChildJoin(contentParentChildJoinDao, subjectEntry, subHeadingEntry, headingCount++);

            } else if (element.hasClass("span6")) {

                System.err.println("Should not come here" + element.text());

            }

        }

        Elements subjectList = document.select("th.span3 a");
        for (Element subject : subjectList) {

            String hrefLink = subject.attr("href");
            File folder = new File(destinationDir, subject.text());
            folder.mkdirs();
            browseSubHeading(hrefLink, folder);


        }


    }

    private void browseSubHeading(String hrefLink, File folder) throws IOException {

        URL subHeadingUrl = new URL(url, hrefLink);
        Document document = Jsoup.connect(subHeadingUrl.toString()).get();

        Elements subHeadingList = document.select("div.row li a");
        for (Element subHeading : subHeadingList) {

            String subHrefLink = subHeading.attr("href");
            String title = subHeading.text();
            File subHeadingFolder = new File(folder, title);
            subHeadingFolder.mkdirs();

            browseSubjects(headingHashMap.get(title), subHrefLink, subHeadingFolder);

        }


    }

    private void browseSubjects(ContentEntry contentEntry, String subHrefLink, File subHeadingFolder) throws IOException {

        URL subjectListUrl = new URL(url, subHrefLink);
        Document document = Jsoup.connect(subjectListUrl.toString()).get();

        Elements subjectList = document.select("dl.results-item");
        for (Element subject : subjectList) {

            Element titleElement = subject.selectFirst("dd.title");
            String title = titleElement != null ? titleElement.text() : EMPTY_STRING;

            Element descriptionElement = subject.selectFirst("dd.description");
            String description = descriptionElement != null ? descriptionElement.text() : EMPTY_STRING;

            Element authorElement = subject.selectFirst("dd.author");
            String author = authorElement != null ? authorElement.text() : EMPTY_STRING;

            Element publisherElement = subject.selectFirst("dd.publisher");
            String publisher = publisherElement != null ? publisherElement.text() : EMPTY_STRING;

            String hrefLink = subject.selectFirst("a").attr("href");

            URL subjectUrl = new URL(url, hrefLink);
            String subjectUrlString = subjectUrl.toString();

            ContentEntry lessonEntry = ContentScraperUtil.createOrUpdateContentEntry(subjectUrl.getQuery(),
                    title, subjectUrlString, publisher, LICENSE_TYPE_CC_BY, englishLang.getLangUid(),
                    null, description, true, author, EMPTY_STRING,
                    EMPTY_STRING, EMPTY_STRING, contentEntryDao);

            ContentScraperUtil.insertOrUpdateParentChildJoin(contentParentChildJoinDao, contentEntry, lessonEntry, subjectCount++);

            EtekkathoScraper scraper = new EtekkathoScraper(subjectUrlString, subHeadingFolder);
            try {
                scraper.scrapeContent();

                String fileName = subjectUrlString.substring(subjectUrlString.indexOf("=") + 1);
                File contentFolder = new File(subHeadingFolder, fileName);
                File content = new File(contentFolder, fileName);

                if (scraper.isUpdated()) {
                    ContentScraperUtil.insertContentEntryFile(content, contentEntryFileDao, contentFileStatusDao,
                            lessonEntry, ContentScraperUtil.getMd5(content), contentEntryFileJoin, true,
                            scraper.getMimeType());

                } else {

                    ContentScraperUtil.checkAndUpdateDatabaseIfFileDownloadedButNoDataFound(content, lessonEntry, contentEntryFileDao,
                            contentEntryFileJoin, contentFileStatusDao, scraper.getMimeType(), true);

                }

            } catch (Exception e) {
                System.err.println("Unable to scrape content from " + title + " at url " + subjectUrlString);
                e.printStackTrace();
            }


        }

        Element nextLink = document.selectFirst("li.next a");
        if (nextLink != null) {
            browseSubjects(contentEntry, nextLink.attr("href"), subHeadingFolder);
        }

    }


}
