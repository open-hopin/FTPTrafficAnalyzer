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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class provides functionality to extract information out of FTP server log files
 * located as zipped files in FTP host's 'logs' directory.
 * This class expects the unzipped and loaded text content of a log file as basis
 * to be handed over to constructor.
 * 
 * Only log file content of the Common Log Format
 * (<a href="https://en.wikipedia.org/wiki/Common_Log_Format">https://en.wikipedia.org/wiki/Common_Log_Format</a>),
 * also known as the NCSA Common log format and as the case may be headed by and/or followed by additional
 * logged content can be analyzed successfully.
 * 
 * @author mb
 *
 */
public class LogsFileAnalyzer {
	/**
	 * Result of the analysis. Keys are the GET instructions with paths and
	 * names of files downloaded and values are related download counts.
	 */
	public TreeMap<String, Integer> tmResult;
	/**
	 * right formatter to parse date with time stamps
	 * in file "traffic.txt" global content
	 * {@link LogsFileAnalyzer#sToAnalyze}.
	 */
	public DateTimeFormatter formatterFound;
	/**
	 * Internal index of formatter found to be suitable
	 * out of supported formatters to read dates with time
	 * stamps in file "traffic.txt". Value -1 stands for
	 * no suitable formatter found.
	 */
	public int iFormatterIndex = -1;
	/**
	 * Member counts no. of total downloads according to content of file "traffic.txt".
	 */
	public int iTotalDownloads = 0;
	/**
	 * Content of file "traffic.txt", which is obtained from one file of FTP host's
	 * "logs" directory in unzipped format and renamed. 
	 */
	public String sToAnalyze;
	/**
	 * first date and time in analyzed log file
	 */
	public OffsetDateTime odtMin = null;
	/**
	 * last date and time in analyzed log file
	 */
	public OffsetDateTime odtMax = null;
	/**
	 * first date and time of ZIP or EXE
	 * (or other endings according to file "config-endings.txt")
	 * download (try) in analyzed log file
	 */
	public OffsetDateTime odtFirst = null;
	/**
	 * last date and time of ZIP or EXE
	 * (or other endings according to file "config-endings.txt")
	 * download (try) in analyzed log file
	 */	
	public OffsetDateTime odtLast = null;

	/**
	 * constructor not to be used
	 */
	private LogsFileAnalyzer() {
		// not extended
	}
	
	/**
	 * constructor related to a certain log file with content as handed over
	 * @param sLogFileContent content of single log file
	 */
	public LogsFileAnalyzer(String sLogFileContent) {
		sToAnalyze = sLogFileContent;
	}

	/**
	 * This method used global variable {@link LogsFileAnalyzer#sToAnalyze} to extract
	 * information, what files have been downloaded and how many times.
	 * Result of the analysis is given back as a collection.
	 * Fills global variable {@link LogsFileAnalyzer#iTotalDownloads}.
	 * @param sEndings endings searched for to be included in the statistics divided
	 * by the or sign, for example "zip|exe", upper and lower case does not matter
	 * @return Collection with keys that are the GET instructions with paths and names
	 * of files downloaded and values are related download counts.
	 * Same object as {@link #tmResult}.
	 */
	public TreeMap<String, Integer> tmFillDownloadsCollection(String sEndings) {
		
		Integer a = 0;
		Boolean bEnd = false;
		
		// Special pattern that is naming the domain directly after number of bytes downloaded.
		// This is a first try on a special logging format, only the second try is universal:
		Pattern pattern = Pattern.compile("(?i)(GET\\s.*?\\.(?:" + sEndings + "))\\s.+?\"\\s(\\d+)\\s\\d+\\s(.+?)\\s\"");	// get all download paths			
	    Matcher matcher = pattern.matcher(sToAnalyze);
	    
	    TreeMap<String, Integer>tm = new TreeMap<>();
	    while(!bEnd) {
		    iTotalDownloads = 0;
		    Integer i;
		    while(matcher.find()) {
		    	iTotalDownloads++;
		        String s = matcher.group(1);
		        
		        if (matcher.groupCount() == 3) s = s.substring(0, 4) + matcher.group(3) + s.substring(4);
		        // else groupCount() is 1 do nothing
		        
		        if (matcher.group(2).equals("404")) s+= " [404 file/page not found]";
		        
		        i = tm.get(s);
		        if (i == null) i = 0;
		        i++;
		        tm.put(s, i);
		    }
		    a++;
		    if (iTotalDownloads > 0 || a > 1) bEnd = true;
		    else {
		    	pattern = Pattern.compile("(?i)(GET\\s.*?\\.(?:" + sEndings + "))\\s.+?\"\\s(\\d+)\\s\\d+");	// get all download paths			
			    matcher = pattern.matcher(sToAnalyze);
		    }
	    }
	    tmResult = tm;
	    return tm;        
	}

	/**
	 * Function gives back a suitable formatter found for date with time
	 * stamps in file "traffic.txt" global content {@link LogsFileAnalyzer#sToAnalyze}. Method
	 * sets also global {@link LogsFileAnalyzer#iFormatterIndex}.
	 * @return Suitable formatter found for date with time
	 * stamps in file "traffic.txt", null when not found.
	 * Same object as {@link #formatterFound}.
	 */
	public DateTimeFormatter fmFindDateTimeFormatter() {
		Pattern pattern = Pattern.compile("\\[.+?\\]"); // get all days to determine minimum and maximum
		Matcher matcher = pattern.matcher(sToAnalyze);		
		
		ArrayList<DateTimeFormatter> alFormatter = new ArrayList<>();		
		
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
				"d/LLL/yyyy:HH:mm:ss XXXX", Locale.ENGLISH); // should be the right formatter // dd in format string changed to d (08.12.2021)
		alFormatter.add(formatter);
		alFormatter.add(DateTimeFormatter.ISO_DATE_TIME); // some other formatter to try
		alFormatter.add(DateTimeFormatter.ISO_INSTANT); // some other formatter to try
		alFormatter.add(DateTimeFormatter.ISO_LOCAL_DATE_TIME); // some other formatter to try
		alFormatter.add(DateTimeFormatter.ISO_OFFSET_DATE_TIME); // some other formatter to try
		alFormatter.add(DateTimeFormatter.ISO_ZONED_DATE_TIME); // some other formatter to try
		alFormatter.add(DateTimeFormatter.RFC_1123_DATE_TIME); // some other formatter to try
		
		iFormatterIndex = -1;
		DateTimeFormatter formatterToReturn = null;
		
		// Determine formatterFound:
		if (matcher.find()) {
			String s = matcher.group().substring(1, matcher.group().length()-1);
			
			for (DateTimeFormatter dtf: alFormatter) {
				iFormatterIndex++;
				try {
					OffsetDateTime.parse(s, dtf);
					formatterToReturn = dtf;					
					break;
				} catch (DateTimeParseException dtex) {					
					//do nothing here
					//dtex.printStackTrace(); possible for debug
				}
			}
		}
		formatterFound = formatterToReturn;
		return formatterToReturn;
	}

	/**
	 * Method fills global time variables according
	 * to global content {@link LogsFileAnalyzer#sToAnalyze}:
	 * - {@link LogsFileAnalyzer#odtMin}
	 * - {@link LogsFileAnalyzer#odtMax}
	 * - {@link LogsFileAnalyzer#odtFirst}
	 * - {@link LogsFileAnalyzer#odtLast}
	 * Method will do nothing if {@link #formatterFound} is null.
	 * @param sEndings endings searched for to be included in the statistics divided
	 * by the or sign, for example "zip|exe", upper and lower case does not matter
	 */
	public void fillGlobalDateTimes(String sEndings) {
		Pattern pattern = Pattern.compile("\\[.+?\\]"); // get all days to determine minimum and maximum
		Matcher matcher = pattern.matcher(sToAnalyze);		
		Pattern outerPattern = Pattern.compile(
				"(?i)\\[.+?\\]\\s\"GET\\s.*?\\.(?:" + sEndings + ")\\s.+?\"\\s\\d+\\s\\d+");	// regex ending \\s.+?\\s\" commented out (06.12.2021) // get all download paths, this time including their time stamps			
	    Matcher outerMatcher = outerPattern.matcher(sToAnalyze);
						
		if (formatterFound != null) { // otherwise dates are not shown
			while (matcher.find()) { // determine odtMin and odtMax
				
				String s = matcher.group().substring(1, matcher.group().length()-1);
				OffsetDateTime offsetdatetime = null;
					
				try {
					offsetdatetime = OffsetDateTime.parse(s, formatterFound);
					
					if (odtMin == null) odtMin = offsetdatetime;
					if (odtMax == null) odtMax = offsetdatetime;
					if (odtMin.isAfter(offsetdatetime)) odtMin = offsetdatetime;
					if (odtMax.isBefore(offsetdatetime)) odtMax = offsetdatetime;
					
				} catch (DateTimeParseException dtex) {
					// do nothing, exceptions will be possible here, due to square bracket used not for dates
					// dtex.printStackTrace();				
				}	
			}
						
			while (outerMatcher.find()) { // determine odtFirst and odtLast
				matcher = pattern.matcher(outerMatcher.group());
				matcher.find();
				
				String s = matcher.group().substring(1, matcher.group().length()-1);
				OffsetDateTime offsetdatetime = null;
					
				try {
					offsetdatetime = OffsetDateTime.parse(s, formatterFound);								
				} catch (DateTimeParseException dtex) {
					dtex.printStackTrace();				
				}
				
				if (odtFirst == null) odtFirst = offsetdatetime;
				if (odtLast == null) odtLast = offsetdatetime;
				if (odtFirst.isAfter(offsetdatetime)) odtFirst = offsetdatetime;
				if (odtLast.isBefore(offsetdatetime)) odtLast = offsetdatetime;
			}
		}	
	}

	

}
