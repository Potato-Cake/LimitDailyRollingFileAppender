
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogTest {
	static Logger logger = LoggerFactory.getLogger(LogTest.class);

	public static void main(String[] args) throws InterruptedException {
		int n = 100;
		for (int i = 0; i < n; i++) {
			Thread thread = new Thread(new Runnable() {

				public void run() {
					while (true) {
						logger.info("{}", System.currentTimeMillis());
					}
				}
			});
			thread.setDaemon(true);
			thread.start();
		}
		while (true) {
			Thread.sleep(20000);
		}
	}
}
