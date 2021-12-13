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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import dyorgio.runtime.run.as.root.RootExecutor;

/**
 * Class contains some static members for general purpose.
 * The members have supporting functionality.
 * 
 * @author mb
 *
 */
public class Utils {
	private static volatile Boolean bUserConfirmedAsAdmin = false;
	/**
	 * Member stores, whether application runs on Windows (value "true")
	 * or not, what means Linux is assumed (value false).
	 */
	static Boolean bWin;
	/**
	 * Member stores suitable file path delimiter for Windows ("\")
	 * or Linux ("/").
	 */
	static String sDelimiter;
	
	static {
		bWin = System.getProperty("os.name").toLowerCase().contains("windows");
		if (bWin) sDelimiter="\\"; else sDelimiter="/";
	}
	
	/**
	 * constructor not to be used
	 */
	private Utils() {
		// not extended
	}
	
	/**
	 * Loads an UTF-8 text file from disk.
	 * Gives out exceptions in case file does not exist or cannot be read from.
	 * 
	 * If file content begins with byte -1 as shown in IDE debug process (-17 as byte data type)
	 * and then byte -2 as shown in IDE debug process (-69 as byte data type),
	 * only the begin of file's content will be indicated therewith,
	 * so these bytes are removed from the beginning of returned value.
	 * 
	 * @param f file object to be loaded as text
	 * @return content String of file handed over as parameter
	 */
	public static String loadText(File f) {
		String sReturn;		
		try {	
			if (!f.exists()) throw new Exception("File: " + f.getAbsolutePath() + " not found.");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		byte[] encoded = null;
		try {
			encoded = Files.readAllBytes(f.toPath());
		} catch (IOException e) {			
			e.printStackTrace();
		}
		sReturn = new String(encoded, StandardCharsets.UTF_8);
		
		// A string read in from file and then beginning with -1 and then -2
		// as shown as value as sequence of bytes in Eclipse IDE debug process
		// has to be modified. The first char is only indicating begin of file
		// and occurred for UTF-8 content sometimes, so read in string's first
		// character has to be removed:
		
		//byte[] bt = sReturn.getBytes(StandardCharsets.UTF_8);
		//System.out.println(encoded[0]);
		//System.out.println(encoded[1]);
		//Byte bt0 = encoded[0];
		//Byte bt1 = encoded[1];
		if (encoded[0] == /*-1*/-17 && encoded[1] == /*-2*/-69) {
		//if (bt0.compareTo((byte)-17) == 0 && bt1.compareTo((byte)-69) == 0) {
			sReturn = sReturn.substring(1);
		}
		
		return sReturn;
	}
	
	/**
	 * Loads text from disk using UTF-8 coding.
	 * @param absoluteOrRelativePathSeenFromMainJar file name including
	 * absolute or relative path seen from application main directory
	 * @return String object of text loaded
	 */
	public static String loadString(String absoluteOrRelativePathSeenFromMainJar) {
				
		File f = new File(absoluteOrRelativePathSeenFromMainJar);
		return loadText(f);
	}
	
	/**
	 * Saves text to disk using UTF-8 coding.
	 * @param absoluteOrRelativePathSeenFromMainJar file name including
	 * absolute or relative path seen from application main directory
	 * @param content to be saved to disk
	 */
	public static void saveString(String absoluteOrRelativePathSeenFromMainJar, String content) {
				
		try (Writer out = new BufferedWriter(
				new OutputStreamWriter( new FileOutputStream(absoluteOrRelativePathSeenFromMainJar), "UTF-8"))) {
		    out.write(content);
		} catch (IOException iex) {
			iex.printStackTrace();
		}
	}
	
	/**
	 * Path for related JAR-file, i.e. Java program
	 * currently executed, is determined and stored
	 * in a new file object. This path does not
	 * include the JAR-file name. Works correctly under
	 * Windows and Linux. Path can contain spaces.
	 * Procedure also works, when called in Preloader thread.
	 * @return new file object with working program's path.
	 * For Windows for example: /E:/PRJ EC/sc/bin/;
	 * for Linux for example: /home/mb/Downloads/_200802 sc dist Linux
	 */
	public static File fActiveJarPathWithoutFilename() {		
		
	    String sMainJar;
	    	    
	    sMainJar = FTPTrafficAnalyzer.class.getProtectionDomain().getCodeSource().getLocation().getPath();
	    
	    // But filename can be different for example a version indication in the name:
	    if ((sMainJar.length()) >3 && (".jar".equalsIgnoreCase(sMainJar.substring(sMainJar.length() - 4)))) {
	        if (sMainJar.lastIndexOf(sDelimiter) == -1) sDelimiter = "/"; //possible under Windows 10 or else
	        sMainJar = sMainJar.substring(0, sMainJar.lastIndexOf(sDelimiter));//minus "/mbFXWords.jar" or similar jar-name
	    }// else class is executed, not jar, so do nothing, because filename is missing already
	    
	    sMainJar = sMainJar.replace("%20", " ");
	    
	    //System.out.println(sMainJar); // For Windows for example: /E:/PRJ EC/sc/bin/, for Linux for example: /home/mb/Downloads/_200802 sc dist Linux      
	    return new File(sMainJar);
	}
	
	/**
	 * Function determines for Linux and Windows, whether handed over path
	 * with or without file name is absolute or relative.
	 * @param sPath path on disk with or without file name to be checked, whether
	 * it is absolute or relative
	 * @return Whether handed over path is absolute (value true) or
	 * relative (value false). In case handed over path is too short
	 * to determine this true is given back.
	 */
	public static Boolean bAbsolutePath(String sPath) {
		Boolean bAbsolute = false;
		if (bWin) {
			if (sPath.length() < 2) return true;
			if (sPath.substring(1,2).equals(":")) bAbsolute = true;
		} else {
			if (sPath.length() < 1) return true;
			if (sPath.substring(0,1).equals("/")) bAbsolute = true;
		}
		return bAbsolute;
	}
	
	/**
	 * 
	 * @param objClass 'class' keyword in Java returns the same as Object.getClass() for a given instance,
	 * but no instance is needed, you can statically state what class at compile time.
	 * In any case it results in giving an object that represents the class of the (original) object.
	 * @return
	 */
	public static Boolean bRestrictedForAdminNeededForAccessOrRestart(Object objClass) {
		
		File fApplicationDir = new File(Utils.fActiveJarPathWithoutFilename().getAbsolutePath());
		
		Boolean bCanWrite = true;
		String sTempName = String.valueOf(System.currentTimeMillis());
		char ch = 'e'; // Any value is suitable here, variable must be initialized.
		File fLock = new File(fApplicationDir.getAbsolutePath() + "/" + sTempName + ".lck");
		
		// fApplicationDir.canWrite(); does not function here, it says writing is possible for non root user
		// even under programs in Windows 10. So I try to indeed write a file for testing:
		try (Writer out = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(fApplicationDir.getAbsolutePath() + "/" + sTempName + ".txt"), "UTF-8"))) {
		    out.write("testing write to application directory");
		} catch (IOException iex) {
			bCanWrite = false;
		} finally {
			if (bCanWrite) new File(fApplicationDir.getAbsolutePath() + "/" + sTempName + ".txt").delete();
		}
		
		if (!bCanWrite) {
			// Show a textual selection menu:			
	        System.out.println("NO WRITING ACCESS TO APPLICATION DIRECTORY.\n"
	        		+ "Security alternatives there are 3 for starting the program:\n"
	        		+ " - Move the portable application or data worked on to some home directory,\n"
	        		+ "   where the user rights are sufficient for full access.\n"
	        		+ " - Set the directory rights to full access manually.\n"
	        		+ " - Or execute the application with administrator privileges.\n"
	        		+ "   In Windows 10 for example for an EXE file it is also possible\n"
	        		+ "   to set this as a standard behavior\n"
	        		+ "   under file properties compatibility tab.\n"
	        		+ "AND THERE ARE 2 ALTERNATIVES TO CHOOSE NOW:\n"
	        		+ " <enter> a - try with administrator rights via the application\n"
	        		+ " <enter> w - use " + objClass.getClass().getCanonicalName() + " without memory functionality\n"
	        		+ " <enter> alone or any other - for END");
	        try {
				ch = (char) System.in.read(); // The first letter of the user input is decisive, others are discarded.
	        } catch (IOException e) {
				e.printStackTrace();
			}
			//System.out.println(ch);				
			if (ch == 'a') {				
				
//				Runnable task = new Runnable() {
//					@Override
//					public void run() {
//						System.out.println("Original VM is closed now.");
//						System.exit(0); // Terminate the virtual machine. It is not needed any more, because another has been started with root privileges.
//					}
//		    	};
//		    	ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
//
//		    	executorService.schedule(task, 1, TimeUnit.SECONDS);
								
				RootExecutor rootExecutor = null;				
				try {
					// Specify JVM options (optional):
					rootExecutor = new RootExecutor();
					rootExecutor.run(() -> {
						//bUserConfirmedAsAdmin = true;
						Utils.saveString(fLock.getAbsolutePath(), "");
						try {
							Runtime.getRuntime().exec("attrib +r \"" + fApplicationDir.getAbsolutePath() + "\"");
							System.out.println("attrib with root");
							/*Object lock = new Object();
							synchronized(lock){
							    try {
									lock.wait(3000);
								} catch (InterruptedException ie) {
									ie.printStackTrace();
								}
							}
							System.out.println("Wait ended.");*/
						} catch (IOException ioex) {
							ioex.printStackTrace();
						}
					});
					synchronized(fLock) {
					    try {
					    	while (!fLock.exists()) {
					    		fLock.wait(1000);
					    	}				    				    	
						} catch (InterruptedException ie) {
							ie.printStackTrace();
						} finally {
							System.out.println("Original Java VM is closed now, new Java admin process for FTPTrafficAnalyzer has been started.");
							fLock.delete();
					    	System.exit(0);	
						}
					}
				} catch (Exception ex) {						
					//ex.printStackTrace();
					System.out.println("I was not able to start administrator process for application FTPTrafficAnalyzer.");
				}
				
				
			}
			
		}
		
		System.out.println("Original program proceeds.");
		return !bCanWrite;
		
	}
}
