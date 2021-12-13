/*
 * Copyright 2021 txt-soft.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 * 
 */
package com.txtsoft.ftptrafficanalyzer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import dyorgio.runtime.run.as.root.NotAuthorizedException;
import dyorgio.runtime.run.as.root.RootExecutor;
import dyorgio.runtime.run.as.root.UserCanceledException;

/**
 * Application FTPTrafficAnalyzer - read out FTP host's log file,
 * giving out download click statistics to Java console.
 * The application only analyzes the Common Log Format and
 * provides a memory effect between its invocations to
 * also provide a long term statistic.
 * 
 * The Common Log Format
 * (<a href="https://en.wikipedia.org/wiki/Common_Log_Format">https://en.wikipedia.org/wiki/Common_Log_Format</a>),
 * also known as the NCSA Common log format, is the default for Apache HTTP Server
 * (<a href="https://en.wikipedia.org/wiki/Apache_HTTP_Server">https://en.wikipedia.org/wiki/Apache_HTTP_Server</a>),
 * colloquially called Apache. The logging format is consisting of:
 * - host ident authuser date request status bytes
 * - for Example: 127.0.0.1 user-identifier frank [10/Oct/2000:13:55:36 -0700] "GET /apache_pb.gif HTTP/1.0" 200 2326
 * Format is set in file: <Apache install directory>\conf\httpd.conf by the line:
 * - LogFormat "%h %l %u %t \"%r\" %>s %b" common
 * Before and after the shown format string you can have, what you like.
 * 
 * Setups packages are including Java Runtime Environment. For own setup
 * compiler compliance level is 11, so you need OpenJDK 11 or later separately installed to run
 * the program. Make sure console/terminal is shown on execution.
 * 
 * FTPTrafficAnalyzer application expects text file "traffic.txt" (lower-case) in application directory
 * to extract how many downloads are indicated and what files, according to
 * files's names and paths, have been downloaded.
 * 
 * - You can see the links used to your download files.
 *   This is useful to keep or get these links alive. Also dead links
 *   cause some traffic, so it is an option to support these
 *   for more valid downloads.
 * - The time range of the file is extracted, additionally date and time
 *   of first and last download are given out. 
 * - Small project, Eclipse without build tool.
 * - Tested with OpenJDK 13 and 17.
 * 
 * EXE and .ZIP download files are extracted and counted per default.
 * To get statistics for other file endings simply change content of file "config-endings.txt"
 * in application main directory. In each line name the extension to evaluate.
 * Instead of download statistics you can achieve click statistics to the pages of your site,
 * so use htm, html instead of zip and exe.
 * 
 * Via command line unnamed parameter you can specify different name of the logging file
 * (with absolute or path relative to application directory) to be analyzed.
 * Instead you can use named parameters, for example:
 * "logfile=traffic.txt" "endingsfile=config-endings.txt"
 * (what are the default values). Parameter HELP gives out explanation to command line
 * parameters.
 * 
 * To get a suitable file containing this logging information go via your host's FTP to directory
 * "logs". There you find suitable files, for example:
 * - "access.log.26.gz" for the calendar week 26
 * - "access.log.34.4.gz" for the calendar week 34 and the 4th day of the week, that is Thursday.
 * These files are all zipped, so unzip for example to "access.log.34.4".
 * Do not forget to copy to application directory and rename to "traffic.txt".
 * 
 * @author mb
 *
 */
public class FTPTrafficAnalyzer {
	
	/**
	 * instance related to a single log file
	 * with String content loaded
	 */
	public static LogsFileAnalyzer analyzer;
	
	/**
	 * constructor not extended so far
	 */
	public FTPTrafficAnalyzer() {
		// not extended
	}

	/**
	 * Entry point of the application.
	 * @param args from command line. One unnamed parameter can be handed
	 * over, the logfile name with absolute or relative path. A relative path
	 * will be seen from application main JAR. You can use named parameters instead,
	 * unnamed parameters will be ignored then: "logfile=", "endingsfile=".
	 * logfile: to be analyzed 
	 * endingsfile: text file with ending to be evaluated
	 * Only parameter -h, h, -help or help gives hints to possible parameters.
	 */
	public static void main(String[] args) {
		
		if (Utils.bRestrictedForAdminNeededForAccessOrRestart(FTPTrafficAnalyzer.class)) {
			System.out.println("Writing to application directory is not possible.");
		} else {
			System.out.println("Writing to application directory is possible.");
		}
		
		String sLogFile = null;
        String sEndingsFile = null;
        
        HashMap<String,String> mapArguments = makeMap(args);
        if (mapArguments.get("logfile") != null)
        	sLogFile = mapArguments.get("logfile").toString();
        if (mapArguments.get("endingsfile") != null)
        	sEndingsFile = mapArguments.get("endingsfile").toString();
        
        //System.out.println("sLogFile: " + sLogFile);
        //System.out.println("sEndingsFile: " + sEndingsFile);
        
        if (mapArguments.isEmpty()) {
        	if (args.length > 0) sLogFile = args[0];
        	if (args.length > 1) System.out.println("Multiple unnamed parameters, parameters after the first are ignored.");
        }
		
        if ("".equals(sLogFile)) sLogFile = null;
        if ("".equals(sEndingsFile)) sEndingsFile = null;
        
        if (sLogFile != null && (sLogFile.toLowerCase().equals("-h") || sLogFile.toLowerCase().equals("h") || sLogFile.toLowerCase().equals("-help") || sLogFile.toLowerCase().equals("help"))) {
			System.out.println("Help to FTPTrafficAnalyzer command line parameters:");
			System.out.println("One unnamed parameter logfile with absolute or relative path is possible.");
			System.out.println("You can use one or two named parameters instead, for example:\n\"logfile=traffic.txt\" \"endingsfile=config-endings.txt\"\nwhat are the default values.");
			// Wait for key input to close console window:
	        System.out.println();
	        System.out.println("Press enter to continue.");
	        try {
				System.in.read();
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.exit(0);
		}
        
		//File fTest = new File("wer");
		//System.out.println(fTest.exists());
		//System.out.println(fTest.getAbsolutePath());
		
		if (sLogFile == null) sLogFile = "traffic.txt";
		if (sEndingsFile == null) sEndingsFile = "config-endings.txt";
		
		File f;
		if (Utils.bAbsolutePath(sLogFile)) {
			f = new File(sLogFile);
		} else {
			f = new File(Utils.fActiveJarPathWithoutFilename().getAbsolutePath() + "/" + sLogFile);
		}
		if (f.exists()) {
			
			System.out.println("File to analyze read: " + f.getAbsolutePath());
			analyzer = new LogsFileAnalyzer(Utils.loadText(f)); // complete file content handed over to constructor
			
			File fEndings;
			if (Utils.bAbsolutePath(sEndingsFile)) {
				fEndings = new File(sEndingsFile);
			} else {
				fEndings = new File(Utils.fActiveJarPathWithoutFilename().getAbsolutePath() + "/" + sEndingsFile);
			}
			String sEndings = loadEndingsSearchedFor(fEndings);
			
			/**
	    	 * Result of the analysis. Keys are the GET instructions with paths and
	    	 * names of files downloaded and values are related download counts.
	    	 */
	    	//TreeMap<String, Integer> tmResult = new TreeMap<>();	    	
	        /*tmResult = */analyzer.tmFillDownloadsCollection(sEndings);
	        
	        /*DateTimeFormatter formatterFound = */analyzer.fmFindDateTimeFormatter();
	        	
			//matcher.reset();
	        analyzer.fillGlobalDateTimes(sEndings);	
			
			        
	        //===TESTING:
	        
	        /*Set<PosixFilePermission> ownerWritable = PosixFilePermissions.fromString("rw-r--r--");
	        FileAttribute<?> permissions = PosixFilePermissions.asFileAttribute(ownerWritable);
	        try {
				Files.createFile(new File("testtest.txt").toPath(), permissions);
			} catch (IOException ioex) {
				ioex.printStackTrace();
			}*/
	        
	        
	        /*
	        // Specify JVM options (optional)
	        RootExecutor rootExecutor = null;
			try {
				rootExecutor = new RootExecutor("-Xmx64m");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

	        // Execute privileged action
	       
			File fApplicationDirOrFile = new File(Utils.fActiveJarPathWithoutFilename().getAbsolutePath() + "/" + "test.txt");
			try {
				Runtime.getRuntime().exec("attrib +r \"" + fApplicationDirOrFile.getAbsolutePath() + "\"");
				System.out.println("attrib without root");
			} catch (IOException ioex) {
				ioex.printStackTrace();
			}
	        try {
				rootExecutor.run(() -> {
					//Utils.saveString(Utils.fActiveJarPathWithoutFilename().getAbsolutePath() + "/" + "test.txt", "1 two 3");
					
			        System.out.println(fApplicationDirOrFile.setExecutable(true, false));
			        System.out.println(fApplicationDirOrFile.setReadable(true, false));
			        System.out.println(fApplicationDirOrFile.setWritable(true, false));
			        
			        try {
						Runtime.getRuntime().exec("attrib +r \"" + fApplicationDirOrFile.getAbsolutePath() + "\"");
						System.out.println("attrib with root");
					} catch (IOException ioex) {
						ioex.printStackTrace();
					}
				});
			} catch (UserCanceledException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NotAuthorizedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        */
	        
	        
	        // Give out the result to console:
	        reportToConsole(analyzer);
			
		} else {
			System.out.println("Not found file to analyze: " + f.getAbsolutePath());
		}
		
		// Wait for key input to close console window:
        System.out.println();
        System.out.println("Press enter to end.");
        try {
			char ch = (char) System.in.read();
			//System.out.println(ch);
		} catch (IOException e) {
			e.printStackTrace();
		}
//		} catch (Exception ex) {
//			ex.printStackTrace();
//			// Wait for key input to close console window:
//	        System.out.println();
//	        System.out.println("Press enter to continue.");
//	        try {
//				System.in.read();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}

	}
	
	/**
	 * Function gives back string of endings in the form for example "zip|exe"
	 * read out of "config-endings.txt" file in application main directory.
	 * Every ending to be determined is stated in new line as content of this file,
	 * in the example this is:
	 *                 "zip
	 *                  exe"
	 * Upper and lower case does not matter for file's content.
	 * 
	 * If file "config-endings.txt" does not exist or is empty, the default
	 * "zip|exe" is given back. Function here gives out message to console,
	 * whether "config-endings.txt" is found and not empty or whether
	 * default is assumed.
	 * 
	 * @param fEndings is a text file with content file endings to be evaluated,
	 * each in one line (upper and lower case does not matter in this file's content).
	 * If file handed over does not exist, the default "zip|exe" will be given back here.
	 * @return string of all endings indicated in file "config-endings.txt"
	 * in the form for example "htm|html". The return value is converted
	 * to lower case.
	 */
	private static String loadEndingsSearchedFor(File fEndings) {
		String sReturn = "";
		Boolean bDefaultUsed = false;
		
		if (fEndings.exists()) {
			String sTemp = Utils.loadText(fEndings);
			String[] se = sTemp.split("\\r?\\n");
			for (String s: se) {
				if (s.trim().length() > 0) sReturn += "|" + s.trim();
			}
			if (sReturn.length() > 0) sReturn = sReturn.substring(1);
		} else { // default:
			sReturn = "zip|exe";
			bDefaultUsed = true;
		}
		if (sReturn.length() == 0) {
			sReturn = "zip|exe";
			bDefaultUsed = true;
		}
		sReturn = sReturn.toLowerCase();
		if (bDefaultUsed) {
			System.out.println("Endings file not found or empty: " + fEndings.getAbsolutePath());
			System.out.println("Default endings assumed: " + sReturn);
		} else {
			System.out.println("Endings file read: " + fEndings.getAbsolutePath());
			System.out.println("Endings searched for: " + sReturn);
		}
		return sReturn;
	}
	
	/**
	 * Method gives out some lines to the Java console with result statistics
	 * of handed over analysis. The dates and times part is only given out as far
	 * as handed over member values, for example {@link LogsFileAnalyzer#odtMin},
	 * are not null.
	 * @param lfAnalyzer to be given out as text to Java console
	 */
	private static void reportToConsole(LogsFileAnalyzer lfAnalyzer) {
		System.out.println("Total downloads: " + lfAnalyzer.iTotalDownloads);
        if (lfAnalyzer.odtMin != null) {
        	System.out.println();
        	System.out.println("Date Formatter with index " + lfAnalyzer.iFormatterIndex + " used.");
        	System.out.print("From: " + lfAnalyzer.odtMin.format(lfAnalyzer.formatterFound));
        	if (lfAnalyzer.odtFirst != null) System.out.print(". First download: " + lfAnalyzer.odtFirst.format(lfAnalyzer.formatterFound));
        	System.out.println();
        	if (!lfAnalyzer.odtMin.isEqual(lfAnalyzer.odtMax)) {
        		System.out.print("To: " + lfAnalyzer.odtMax.format(lfAnalyzer.formatterFound));
        		if (lfAnalyzer.odtLast != null) System.out.print(". Last download: " + lfAnalyzer.odtLast.format(lfAnalyzer.formatterFound));
        		System.out.println();
        	}           
            System.out.println();
        }
        
        //System.out.println(lfAnalyzer.tmResult);
        if (lfAnalyzer.tmResult != null) {
        	for(Map.Entry<String,Integer> entry : lfAnalyzer.tmResult.entrySet()) {
        		  String key = entry.getKey();
        		  Integer value = entry.getValue();

        		  System.out.println(key + " => " + value);
        	} 
        }
	}
	
	/**
     * Splits command line arguments of a main() method
     * by "=" into key and value pairs. Converts all
     * keys to complete lower case.
     * @param args command line arguments of
     * for example {@link FTPTrafficAnalyzer#main(java.lang.String[])} 
     * @return HashMap containing named parameters in lower-case
     */
    private static HashMap<String,String> makeMap(String[] args) {
        HashMap<String,String> mapLocal = new HashMap<>();
        
        for (String arg : args) {
            if (arg.contains("=")) {
                //works only if the key doesn't have any '='
                mapLocal.put(arg.substring(0, arg.indexOf('=')),
                        arg.substring(arg.indexOf('=') + 1));
            }
        }
        return mapLocal;
    }
}
