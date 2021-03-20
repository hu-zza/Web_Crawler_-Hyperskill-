package crawler;

import java.awt.Color;
import java.io.BufferedInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

public class WebCrawler extends JFrame {

  // "ConcurrentHashSet" for all the fetched URLs
  static final ConcurrentHashMap<String, Byte> FETCHED_LINKS = new ConcurrentHashMap<>();
  // "DEPTH" + "\t" + "URL"
  static final LinkedBlockingQueue<String> TASKS = new LinkedBlockingQueue<>();
  // <URL, TITLE>
  static final ConcurrentHashMap<String, String> LINKS = new ConcurrentHashMap<>();
  private static final boolean DEBUG = false;
  private static final Pattern PAGE_TITLE_PATTERN = Pattern.compile("<title>(.*)</title>");
  private static final Pattern A_HREF_PATTERN =
      Pattern.compile("href\\s*=\\s*['\"](.*?)['\"].*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern FULL_URL_PATTERN =
      Pattern.compile("https?://(?:www)?\\S*", Pattern.CASE_INSENSITIVE);
  private static final ThreadGroup WORKERS = new ThreadGroup("workers");
  static volatile int maxDepth;
  static volatile boolean fetching;
  private static int workersCount;
  private static Thread[] workers;
  private static long fetchingStartTime;
  private static String baseUrl;

  private final JLabel elapsedLabel;
  private final JLabel parsedLabel;

  private final JTextField urlTextField;
  private final JTextField workersTextField;
  private final JTextField depthTextField;
  private final JTextField timeTextField;
  private final JTextField exportUrlTextField;

  private final JCheckBox depthCheckBox;
  private final JCheckBox timeCheckBox;

  private final JToggleButton runButton;

  public WebCrawler() {
    ///////////////////////
    // APPLICATION WINDOW

    super("Web Crawler");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(600, 342);
    setLocationRelativeTo(null);
    setLayout(null);

    ///////////////////////
    // PANELS

    JPanel settingsPanel = new JPanel(null);
    settingsPanel.setBounds(20, 20, 560, 175);
    settingsPanel.setBackground(new Color(0, 0, 0, 20));
    add(settingsPanel);

    JPanel exportPanel = new JPanel(null);
    exportPanel.setBounds(20, 196, 560, 90);
    exportPanel.setBackground(new Color(0, 0, 0, 20));
    add(exportPanel);

    ///////////////////////
    // FINAL LABELS

    JLabel tmpLabel;

    tmpLabel = new JLabel("Start URL: ");
    tmpLabel.setBounds(10, 10, 110, 30);
    settingsPanel.add(tmpLabel);

    tmpLabel = new JLabel("Workers:");
    tmpLabel.setBounds(10, 50, 110, 30);
    settingsPanel.add(tmpLabel);

    tmpLabel = new JLabel("Max. depth:");
    tmpLabel.setBounds(10, 90, 110, 30);
    settingsPanel.add(tmpLabel);

    tmpLabel = new JLabel("Time limit (s):");
    tmpLabel.setBounds(10, 130, 110, 30);
    settingsPanel.add(tmpLabel);

    tmpLabel = new JLabel("Elapsed time:");
    tmpLabel.setBounds(10, 10, 110, 30);
    exportPanel.add(tmpLabel);

    tmpLabel = new JLabel("Parsed pages:");
    tmpLabel.setBounds(230, 10, 110, 30);
    exportPanel.add(tmpLabel);

    tmpLabel = new JLabel("Export links:");
    tmpLabel.setBounds(10, 50, 110, 30);
    exportPanel.add(tmpLabel);

    ///////////////////////
    //  DISPLAY LABELS

    elapsedLabel = new JLabel("0:00");
    elapsedLabel.setBounds(120, 10, 100, 30);
    exportPanel.add(elapsedLabel);

    parsedLabel = new JLabel("0");
    parsedLabel.setName("ParsedLabel");
    parsedLabel.setBounds(340, 10, 100, 30);
    exportPanel.add(parsedLabel);

    ///////////////////////
    //  TEXT FIELDS

    urlTextField = new JTextField();
    urlTextField.setName("UrlTextField");
    urlTextField.setBounds(120, 11, 430, 30);
    settingsPanel.add(urlTextField);

    workersTextField = new JTextField();
    workersTextField.setName("WorkersTextField");
    workersTextField.setBounds(120, 51, 310, 30);
    workersTextField.setText("10");
    settingsPanel.add(workersTextField);

    depthTextField = new JTextField();
    depthTextField.setName("DepthTextField");
    depthTextField.setBounds(120, 91, 310, 30);
    depthTextField.setText("10");
    settingsPanel.add(depthTextField);

    timeTextField = new JTextField();
    timeTextField.setName("TimeTextField");
    timeTextField.setBounds(120, 131, 310, 30);
    timeTextField.setText("300");
    settingsPanel.add(timeTextField);

    exportUrlTextField = new JTextField();
    exportUrlTextField.setName("ExportUrlTextField");
    exportUrlTextField.setBounds(120, 50, 430, 30);
    exportPanel.add(exportUrlTextField);

    ///////////////////////
    //  CHECKBOXES

    depthCheckBox = new JCheckBox(" Enabled");
    depthCheckBox.setName("DepthCheckBox");
    depthCheckBox.setBounds(450, 90, 100, 30);
    depthCheckBox.setSelected(true);
    settingsPanel.add(depthCheckBox);

    timeCheckBox = new JCheckBox(" Enabled");
    timeCheckBox.setName("TimeCheckBox");
    timeCheckBox.setBounds(450, 130, 100, 30);
    timeCheckBox.setSelected(true);
    settingsPanel.add(timeCheckBox);

    ///////////////////////
    //  BUTTONS

    runButton = new JToggleButton("Run");
    runButton.setName("RunButton");
    runButton.setBounds(450, 50, 100, 30);
    runButton.addActionListener(e -> startFetching());
    settingsPanel.add(runButton);

    JButton exportButton = new JButton("Save");
    exportButton.setName("ExportButton");
    exportButton.setBounds(450, 10, 100, 30);
    exportButton.addActionListener(e -> startExporting());
    // exportButton.setEnabled(false);
    exportPanel.add(exportButton);

    setVisible(true);
  }

  ///////////////////////
  //  ENTRY METHODS

  private void startFetching() {
    if (!fetching) {
      fetching = true;
      displayFetchingInfo();
      runButton.setText("Stop");
      // exportButton.setEnabled(false);
      this.repaint();
      FETCHED_LINKS.clear();
      TASKS.clear();
      LINKS.clear();

      var url = completeBaseUrl();
      if ("".equals(url)) {
        runButton.setSelected(false);
        runButton.setText("Run");
        // exportButton.setEnabled(true);
        displayFetchingInfo(true);
        this.setTitle("Web Crawler - Error: Base URL is insufficient");
        return;
      }

      baseUrl = url.substring(0, url.lastIndexOf('/') + 1);

      maxDepth = depthCheckBox.isSelected() ? Integer.parseInt(depthTextField.getText()) : 0;
      workersCount = Integer.parseInt(workersTextField.getText());
      int maxExecutionTime =
          timeCheckBox.isSelected() ? Integer.parseInt(timeTextField.getText()) : 0;

      executeTask(url, 0);

      workers = new Thread[workersCount];
      hireWorkers();

      long elapsedTime;
      int hysteresis = 0;

      do {
        try {
          Thread.sleep(1_000);
        } catch (InterruptedException e) {
          if (DEBUG) {
            System.err.printf("InterruptedException! %s%n", e);
          }
        }

        if (TASKS.size() > 0) {
          if (hysteresis > 0) {
            hysteresis--;
          }
        } else {
          hysteresis++;
        }

        hireWorkers();
        elapsedTime = displayFetchingInfo();
      } while (hysteresis <= 10 && (maxExecutionTime == 0 || maxExecutionTime >= elapsedTime));
    }
    fetching = false;
    runButton.setSelected(false);
    runButton.setText("Run");
    // exportButton.setEnabled(true);
    displayFetchingInfo(true);
  }

  private void startExporting() {
    // runButton.setEnabled(false);
    var path = exportUrlTextField.getText();
    if (path == null || "".equals(path)) {
      this.setTitle("Web Crawler - Error: Export URL is insufficient");
      // runButton.setEnabled(true);
      return;
    }

    try (var pw = new PrintWriter(new FileWriter(path))) {
      for (var entry : LINKS.entrySet()) {
        pw.printf("%s%n%s%n", entry.getKey(), entry.getValue());
      }
      this.setTitle("Web Crawler - Exported successfully");

    } catch (IOException e) {
      if (DEBUG) {
        System.err.printf("IOException! %s%n", e);
      }
      this.setTitle("Web Crawler - Error: IOError during the export");

    } finally {
      // runButton.setEnabled(true);
    }
  }

  ///////////////////////
  //  INSTANCE METHODS
  private long displayFetchingInfo() {
    return displayFetchingInfo(false);
  }

  private String completeBaseUrl() {
    var url = urlTextField.getText();

    if (url == null || "".equals(url)) {
      return "";
    }

    if (!(url.startsWith("http://") || url.startsWith("https://"))) {
      url = "http://" + url;
    }

    if (!url.endsWith("/") && validateUrl(url) == validateUrl(url + "/")) {
      url += "/";
    }

    urlTextField.setText(url);
    return url;
  }

  private long displayFetchingInfo(boolean fetchingIsOver) {
    if (fetchingStartTime == 0L) {
      fetchingStartTime = System.currentTimeMillis();
    }

    long elapsedTime = (System.currentTimeMillis() - fetchingStartTime) / 1_000;
    String parsedCount = String.valueOf(LINKS.size());

    // reset start time for the next run
    if (fetchingIsOver) {
      fetchingStartTime = 0L;
    }

    var textToDisplay = new StringBuilder();

    if (elapsedTime >= 86400) {
      textToDisplay.append(elapsedTime / 86400);
      textToDisplay.append(elapsedTime >= 172800 ? " days " : "day ");
      elapsedTime /= 86400;
    }

    if (elapsedTime >= 3600) {
      textToDisplay.append(elapsedTime / 3600);
      elapsedTime /= 3600;
      textToDisplay.append(elapsedTime >= 600 ? ":" : ":0");
    }

    textToDisplay.append(elapsedTime / 60);
    elapsedTime %= 60;

    textToDisplay.append(elapsedTime >= 10 ? ":" : ":0");
    textToDisplay.append(elapsedTime);

    elapsedLabel.setText(textToDisplay.toString());
    parsedLabel.setText(parsedCount);
    this.repaint();

    String titlePrefix = fetchingIsOver ? "Web Crawler - Completed: " : "Web Crawler - Fetching: ";
    textToDisplay.append(" (").append(parsedCount).append(")");

    this.setTitle(titlePrefix + textToDisplay.toString());
    return elapsedTime;
  }

  static void executeTask(String url, int depth) {
    var fetchedHTML = fetchTextHtml(url);
    var title = extractTitle(fetchedHTML);

    if (!("".equals(fetchedHTML) || "".equals(title))) {
      LINKS.put(url, title);
    }

    if (depth++ < maxDepth) {
      for (var link : extractLinks(fetchedHTML, baseUrl)) {
        if (!FETCHED_LINKS.containsKey(link)) {
          FETCHED_LINKS.put(link, (byte) 0);
          TASKS.offer(String.format("%d\t%s", depth, link));
        }
      }
    }
  }

  ///////////////////////
  //  STATIC METHODS

  private static void hireWorkers() {
    for (int i = 0; i < workersCount; i++) {
      if (workers[i] == null || workers[i].getState() == Thread.State.TERMINATED) {
        workers[i] = new Thread(WORKERS, new Worker());
        workers[i].start();
      }
    }
  }

  private static int validateUrl(String url) {
    try {
      var conn = (HttpURLConnection) new URL(url).openConnection();
      conn.disconnect();
      return conn.getResponseCode();
    } catch (IOException e) {
      if (DEBUG) System.err.printf("IOException! %s%n", e);
    }

    return -1;
  }

  private static String fetchTextHtml(String url) {
    try {
      var conn = new URL(url).openConnection();
      conn.setRequestProperty(
          "User-Agent",
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0");

      // The crawler process only "text/html" content.
      // Contents without content type are also rejected.
      if (conn.getContentType() != null && !conn.getContentType().startsWith("text/html")) {
        return "";
      }

      try (var inputStream = new BufferedInputStream(conn.getInputStream())) {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      }
    } catch (MalformedURLException e) {
      if (DEBUG) {
        System.err.printf("Malformed URL (%s)!%n", url);
      }
    } catch (IllegalArgumentException e) {
      if (DEBUG) {
        System.err.printf("IllegalArgumentException! %s%n", url);
      }
    } catch (IOException e) {
      if (DEBUG) {
        System.err.printf("IOException! %s%n", e);
      }
    }
    return "";
  }

  private static String extractTitle(String fetchedHTML) {
    var matcher = PAGE_TITLE_PATTERN.matcher(fetchedHTML);
    return matcher.find() ? matcher.group(1) : "";
  }

  private static ArrayList<String> extractLinks(String fetchedHTML, String baseUrl) {
    var links = new ArrayList<String>();
    var matcher = A_HREF_PATTERN.matcher(fetchedHTML);

    while (matcher.find()) {
      String url = completeUrl(baseUrl, matcher.group(1));
      if (DEBUG) {
        System.out.println(url);
      }
      links.add(url);
    }

    return links;
  }

  private static String completeUrl(String baseUrl, String urlFragment) {
    if (urlFragment == null || baseUrl == null) {
      return "";
    }

    if ("".equals(urlFragment) || "#".equals(urlFragment)) {
      return baseUrl;
    }

    var matcher = FULL_URL_PATTERN.matcher(urlFragment);
    if (matcher.matches()) {
      return urlFragment;
    }

    if (urlFragment.startsWith("www.")) {
      return "http://" + urlFragment;
    }

    if (urlFragment.startsWith("//")) {
      return "http:" + urlFragment;
    } else if (urlFragment.startsWith("/")) {
      return baseUrl + urlFragment.substring(1);
    } else if (validateUrl(baseUrl + urlFragment) == 200) {
      return baseUrl + urlFragment;
    } else {
      return "http://" + urlFragment;
    }
  }
}
