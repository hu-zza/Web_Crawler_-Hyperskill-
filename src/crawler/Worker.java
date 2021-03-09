package crawler;

import static crawler.WebCrawler.TASKS;
import static crawler.WebCrawler.executeTask;
import static crawler.WebCrawler.fetching;
import static crawler.WebCrawler.maxDepth;

public class Worker implements Runnable {

  @Override
  public void run() {
    while (!TASKS.isEmpty() && fetching) {
      String task = TASKS.poll();

      if (task != null) {
        String[] taskParts = task.split("\t", 2);
        int depth = Integer.parseInt(taskParts[0]);

        if (maxDepth == 0 || depth <= maxDepth) {
          executeTask(taskParts[1], depth);
        }
      }
    }
  }
}
