package org.apache.log4j;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.log4j.helpers.CountingQuietWriter;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.spi.LoggingEvent;

public class LimitDailyRollingFileAppender extends FileAppender {

	static final int TOP_OF_TROUBLE = -1;
	static final int TOP_OF_MINUTE = 0;
	static final int TOP_OF_HOUR = 1;
	static final int HALF_DAY = 2;
	static final int TOP_OF_DAY = 3;
	static final int TOP_OF_WEEK = 4;
	static final int TOP_OF_MONTH = 5;

	private String datePattern = "'.'yyyy-MM-dd";

	private String scheduledFilename;

	private long nextCheck = System.currentTimeMillis() - 1;

	Date now = new Date();

	SimpleDateFormat sdf;

	RollingCalendar rc = new RollingCalendar();

	int checkPeriod = TOP_OF_TROUBLE;

	static final TimeZone gmtTimeZone = TimeZone.getTimeZone("GMT");

	private long maxFileSize = 10 * 1024 * 1024;

	private long nextRollover = 0;

	private int fileRollingIndex = 1;

	private long rollingInterval = 24 * 60 * 60 * 1000;

	private int keepPeriod = -1;

	public LimitDailyRollingFileAppender() {
	}

	public LimitDailyRollingFileAppender(Layout layout, String filename, String datePattern) throws IOException {
		super(layout, filename, true);
		this.datePattern = datePattern;
		activateOptions();
	}

	public void setDatePattern(String pattern) {
		datePattern = pattern;
	}

	public String getDatePattern() {
		return datePattern;
	}

	public void activateOptions() {
		super.activateOptions();
		if (datePattern != null && fileName != null) {
			now.setTime(System.currentTimeMillis());
			sdf = new SimpleDateFormat(datePattern);
			int type = computeCheckPeriod();
			printPeriodicity(type);
			rc.setType(type);
			File file = new File(fileName);
			scheduledFilename = fileName + sdf.format(new Date(file.lastModified()));
			Date next = rc.getNextCheckDate(now);
			rollingInterval = rc.getNextCheckMillis(next) - next.getTime();
		} else {
			LogLog.error("Either File or DatePattern options are not set for appender [" + name + "].");
		}
	}

	void printPeriodicity(int type) {
		switch (type) {
		case TOP_OF_MINUTE:
			LogLog.debug("Appender [" + name + "] to be rolled every minute.");
			break;
		case TOP_OF_HOUR:
			LogLog.debug("Appender [" + name + "] to be rolled on top of every hour.");
			break;
		case HALF_DAY:
			LogLog.debug("Appender [" + name + "] to be rolled at midday and midnight.");
			break;
		case TOP_OF_DAY:
			LogLog.debug("Appender [" + name + "] to be rolled at midnight.");
			break;
		case TOP_OF_WEEK:
			LogLog.debug("Appender [" + name + "] to be rolled at start of week.");
			break;
		case TOP_OF_MONTH:
			LogLog.debug("Appender [" + name + "] to be rolled at start of every month.");
			break;
		default:
			LogLog.warn("Unknown periodicity for appender [" + name + "].");
		}
	}

	int computeCheckPeriod() {
		RollingCalendar rollingCalendar = new RollingCalendar(gmtTimeZone, Locale.getDefault());
		Date epoch = new Date(0);
		if (datePattern != null) {
			for (int i = TOP_OF_MINUTE; i <= TOP_OF_MONTH; i++) {
				SimpleDateFormat simpleDateFormat = new SimpleDateFormat(datePattern);
				simpleDateFormat.setTimeZone(gmtTimeZone);
				String r0 = simpleDateFormat.format(epoch);
				rollingCalendar.setType(i);
				Date next = new Date(rollingCalendar.getNextCheckMillis(epoch));
				String r1 = simpleDateFormat.format(next);
				if (r0 != null && r1 != null && !r0.equals(r1)) {
					return i;
				}
			}
		}
		return TOP_OF_TROUBLE;
	}

	void dailyRollOver() throws IOException {

		if (datePattern == null) {
			errorHandler.error("Missing DatePattern option in rollOver().");
			return;
		}

		String datedFilename = fileName + sdf.format(now);
		if (scheduledFilename.equals(datedFilename)) {
			return;
		}

		if (keepPeriod > 0) {
			String deleteFileName = fileName + sdf.format(new Date(nextCheck - (keepPeriod + 2) * rollingInterval));
			for (int i = 1;; i++) {
				File target = new File(deleteFileName + "." + i);
				if (target.exists()) {
					target.delete();
					LogLog.debug("delete log file -> " + deleteFileName);
				} else {
					break;
				}
			}
		}

		this.closeFile();

		File target = new File(scheduledFilename + "." + fileRollingIndex);
		if (target.exists()) {
			target.delete();
		}

		File file = new File(fileName);
		boolean result = file.renameTo(target);
		if (result) {
			LogLog.debug(fileName + " -> " + scheduledFilename);
		} else {
			LogLog.error("Failed to rename [" + fileName + "] to [" + scheduledFilename + "].");
		}

		try {
			this.setFile(fileName, true, this.bufferedIO, this.bufferSize);
			nextRollover = 0;
			fileRollingIndex = 1;
		} catch (IOException e) {
			errorHandler.error("setFile(" + fileName + ", true) call failed.");
		}
		scheduledFilename = datedFilename;
	}

	public void sizeRollOver() {
		File target;
		File file;

		if (qw != null) {
			long size = ((CountingQuietWriter) qw).getCount();
			LogLog.debug("rolling over count=" + size);
			nextRollover = size + maxFileSize;
		}

		target = new File(scheduledFilename + "." + fileRollingIndex);

		if (target.exists()) {
			while (true) {
				fileRollingIndex++;
				target = new File(scheduledFilename + "." + fileRollingIndex);
				if (!target.exists()) {
					break;
				}
			}
		}

		boolean renameSucceeded = true;

		this.closeFile();

		file = new File(fileName);
		LogLog.debug("Renaming file " + file + " to " + target);
		renameSucceeded = file.renameTo(target);

		if (renameSucceeded) {
			try {
				this.setFile(fileName, false, bufferedIO, bufferSize);
				fileRollingIndex++;
				nextRollover = 0;
			} catch (IOException e) {
				if (e instanceof InterruptedIOException) {
					Thread.currentThread().interrupt();
				}
				LogLog.error("setFile(" + fileName + ", false) call failed.", e);
			}
		} else {
			try {
				this.setFile(fileName, true, bufferedIO, bufferSize);
			} catch (IOException e) {
				if (e instanceof InterruptedIOException) {
					Thread.currentThread().interrupt();
				}
				LogLog.error("setFile(" + fileName + ", true) call failed.", e);
			}
		}
	}

	public synchronized void setFile(String fileName, boolean append, boolean bufferedIO, int bufferSize)
			throws IOException {
		super.setFile(fileName, append, this.bufferedIO, this.bufferSize);
		if (append) {
			File f = new File(fileName);
			((CountingQuietWriter) qw).setCount(f.length());
		}
	}

	public void setKeepPeriod(int keepPeriod) {
		this.keepPeriod = keepPeriod;
	}

	public void setMaxFileSize(String value) {
		maxFileSize = OptionConverter.toFileSize(value, maxFileSize + 1);
	}

	protected void setQWForFiles(Writer writer) {
		this.qw = new CountingQuietWriter(writer, errorHandler);
	}

	protected void subAppend(LoggingEvent event) {
		long n = System.currentTimeMillis();
		if (n >= nextCheck) {
			now.setTime(n);
			nextCheck = rc.getNextCheckMillis(now);
			try {
				dailyRollOver();
			} catch (IOException ioe) {
				if (ioe instanceof InterruptedIOException) {
					Thread.currentThread().interrupt();
				}
				LogLog.error("rollOver() failed.", ioe);
			}
		}
		super.subAppend(event);
		if (fileName != null && qw != null) {
			long size = ((CountingQuietWriter) qw).getCount();
			if (size >= maxFileSize && size >= nextRollover) {
				sizeRollOver();
			}
		}
	}
}
