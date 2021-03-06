/*
*  Copyright (c) 2005-2013, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.connector.googlespreadsheet;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.wso2.carbon.mediation.library.connectors.core.AbstractConnector;
import org.wso2.carbon.mediation.library.connectors.core.ConnectException;

import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.ServiceException;

public class GoogleSpreadsheetImportCSV extends AbstractConnector {

	public static final String WORKSHEET_NAME = "worksheet.name";
	public static final String SPREADSHEET_NAME = "spreadsheet.name";
	public static final String CSV_NAME = "csv.name";
	public static final String BATCH_ENABLE = "batch.enable";
	public static final String BATCH_SIZE = "batch.size";
	
	private static Log log = LogFactory
			.getLog(GoogleSpreadsheetImportCSV.class);

	public void connect(MessageContext messageContext) throws ConnectException {
		try {
			String worksheetName = GoogleSpreadsheetUtils
					.lookupFunctionParam(messageContext, WORKSHEET_NAME);
			String spreadsheetName = GoogleSpreadsheetUtils
					.lookupFunctionParam(messageContext, SPREADSHEET_NAME);
			String csvName = GoogleSpreadsheetUtils
					.lookupFunctionParam(messageContext, CSV_NAME);
			String batchEnable = GoogleSpreadsheetUtils
					.lookupFunctionParam(messageContext, BATCH_ENABLE);
			String batchSize = GoogleSpreadsheetUtils
					.lookupFunctionParam(messageContext, BATCH_SIZE);
			
			if (worksheetName == null || "".equals(worksheetName.trim())
					|| spreadsheetName == null
					|| "".equals(spreadsheetName.trim()) || csvName == null || "".equals(csvName.trim())) {
				log.info("Please make sure you have given a valid input for the worksheet, spreadsheet and csv name");
				return;
			}

			SpreadsheetService ssService = new GoogleSpreadsheetClientLoader(
					messageContext).loadSpreadsheetService();

			GoogleSpreadsheet gss = new GoogleSpreadsheet(ssService);

			SpreadsheetEntry ssEntry = gss
					.getSpreadSheetsByTitle(spreadsheetName);

			GoogleSpreadsheetWorksheet gssWorksheet = new GoogleSpreadsheetWorksheet(
					ssService, ssEntry.getWorksheetFeedUrl());

			WorksheetEntry wsEntry = gssWorksheet
					.getWorksheetByTitle(worksheetName);

			GoogleSpreadsheetCellData gssData = new GoogleSpreadsheetCellData(
					ssService);
			
			if(batchEnable.equalsIgnoreCase("true")) {
				
				 // Build list of cell addresses to be filled in
			    List<GoogleSpreadsheetCellAddress> cellAddrs = new ArrayList<GoogleSpreadsheetCellAddress>();
			    GoogleSpreadsheetBatchUpdater gssBatchUpdater = new GoogleSpreadsheetBatchUpdater(ssService);
			    int batchSizeInt = 0;
			    if(batchSize != null) {
			    	try {
			    		 batchSizeInt = Integer.parseInt(batchSize);
			    	} catch (NumberFormatException ex) {
			    		System.out.println("Please enter valid number for batch size");
			    	}
			    }
			   
			    
			    if (csvName.equalsIgnoreCase("MessageContext")) {

					if (messageContext.getEnvelope().getBody().getFirstElement() != null) {
						String data = messageContext.getEnvelope().getBody()
								.getFirstElement().getText();
						// convert String into InputStream
						InputStream is = new ByteArrayInputStream(data.getBytes());

						// read it with BufferedReader
						BufferedReader br = new BufferedReader(
								new InputStreamReader(is));

						String line;
						int rowNumber = 1;
						if(batchSizeInt > 0) {
						while ((line = br.readLine()) != null) {
							String[] recordList = line.split(",");
							for (int i = 1; i <= recordList.length; i++) {								
								cellAddrs.add(new GoogleSpreadsheetCellAddress(rowNumber, i, recordList[i - 1]));
							}
							if((rowNumber%batchSizeInt) == 0) {
								gssBatchUpdater.updateBatch(wsEntry, cellAddrs);
								cellAddrs.clear();
							}
							rowNumber++;
						}
						if((rowNumber%batchSizeInt) != 0) {
							gssBatchUpdater.updateBatch(wsEntry, cellAddrs);
						}
						br.close();
						} else {
							while ((line = br.readLine()) != null) {
								String[] recordList = line.split(",");
								for (int i = 1; i <= recordList.length; i++) {								
									cellAddrs.add(new GoogleSpreadsheetCellAddress(rowNumber, i, recordList[i - 1]));
								}								
								rowNumber++;
							}
							br.close();
							gssBatchUpdater.updateBatch(wsEntry, cellAddrs);
						}
						
					}

				} else {

					BufferedReader br = new BufferedReader(new FileReader(csvName));
					String line;
					int rowNumber = 1;
					if(batchSizeInt > 0) {
					while ((line = br.readLine()) != null) {
						String[] recordList = line.split(",");
						for (int i = 1; i <= recordList.length; i++) {
							cellAddrs.add(new GoogleSpreadsheetCellAddress(rowNumber, i, recordList[i - 1]));
						}
						if((rowNumber%batchSizeInt) == 0) {
							gssBatchUpdater.updateBatch(wsEntry, cellAddrs);
							cellAddrs.clear();
						}
						rowNumber++;
					}
					if((rowNumber%batchSizeInt) != 0) {
						gssBatchUpdater.updateBatch(wsEntry, cellAddrs);
					}
					br.close();
					} else {
						while ((line = br.readLine()) != null) {
							String[] recordList = line.split(",");
							for (int i = 1; i <= recordList.length; i++) {
								cellAddrs.add(new GoogleSpreadsheetCellAddress(rowNumber, i, recordList[i - 1]));
							}							
							rowNumber++;
						}
						br.close();
						gssBatchUpdater.updateBatch(wsEntry, cellAddrs);
					}
				}
				
			} else {			

			if (csvName.equalsIgnoreCase("MessageContext")) {

				if (messageContext.getEnvelope().getBody().getFirstElement() != null) {
					String data = messageContext.getEnvelope().getBody()
							.getFirstElement().getText();
					// convert String into InputStream
					InputStream is = new ByteArrayInputStream(data.getBytes());

					// read it with BufferedReader
					BufferedReader br = new BufferedReader(
							new InputStreamReader(is));

					String line;
					int rowNumber = 1;
					while ((line = br.readLine()) != null) {
						String[] recordList = line.split(",");
						for (int i = 1; i <= recordList.length; i++) {
							gssData.setCell(wsEntry, rowNumber, i,
									recordList[i - 1]);
						}
						rowNumber++;
					}
					br.close();
				}

			} else {

				BufferedReader br = new BufferedReader(new FileReader(csvName));
				String line;
				int rowNumber = 1;
				while ((line = br.readLine()) != null) {
					String[] recordList = line.split(",");
					for (int i = 1; i <= recordList.length; i++) {
						gssData.setCell(wsEntry, rowNumber, i,
								recordList[i - 1]);
					}
					rowNumber++;
				}
				br.close();
			}
			}

		} catch (IOException te) {
			log.error("Failed to show status: " + te.getMessage(), te);
			GoogleSpreadsheetUtils.storeErrorResponseStatus(
					messageContext, te);
		} catch (ServiceException te) {
			log.error("Failed to show status: " + te.getMessage(), te);
			GoogleSpreadsheetUtils.storeErrorResponseStatus(
					messageContext, te);
		}
	}


}
