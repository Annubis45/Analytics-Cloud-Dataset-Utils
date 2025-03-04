/*
 * Copyright (c) 2014, salesforce.com, inc.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided 
 * that the following conditions are met:
 * 
 *    Redistributions of source code must retain the above copyright notice, this list of conditions and the 
 *    following disclaimer.
 *  
 *    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and 
 *    the following disclaimer in the documentation and/or other materials provided with the distribution. 
 *    
 *    Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or 
 *    promote products derived from this software without specific prior written permission.
 *  
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED 
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A 
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR 
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED 
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING 
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.sforce.dataset;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.text.DecimalFormat;
import java.util.Properties;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sforce.dataset.flow.monitor.DataFlowMonitorUtil;
import com.sforce.dataset.flow.monitor.Session;
import com.sforce.dataset.loader.DatasetLoader;
import com.sforce.dataset.loader.DatasetLoaderException;
import com.sforce.dataset.loader.EbinFormatWriter;
import com.sforce.dataset.loader.file.schema.ext.ExternalFileSchema;
import com.sforce.dataset.server.DatasetUtilServer;
import com.sforce.dataset.util.CharsetChecker;
import com.sforce.dataset.util.DatasetDownloader;
import com.sforce.dataset.util.DatasetUtils;
import com.sforce.dataset.util.XmdUploader;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.ws.ConnectionException;

import static com.sforce.dataset.DatasetUtilConstants.INCREMENTAL_MODE_INCREMENTAL;
import static com.sforce.dataset.DatasetUtilConstants.INCREMENTAL_MODE_NONE;

@SuppressWarnings("deprecation")
public class DatasetUtilMain {
	
	@SuppressWarnings("unused")
	private static final boolean isJdk14LoggerConfigured = DatasetUtils.configureLog4j();	
	
	public static final String[][] validActions = {{"load","Load CSV"}, {"downloadXMD","Download All XMD Json Files"}, {"uploadXMD","Upload User XMD Json File"}, {"detectEncoding","Detect file encoding"}, {"downloadErrorFile","Fetch CSV Upload Error Report"}};

	public static void main(String[] args) {

		printBanner();
		printClasspath();

		DatasetUtilParams params = new DatasetUtilParams();

		if(args.length>0)
			params.server = false;

		System.out.println("");
		System.out.println("DatsetUtils called with {"+args.length+"} Params:");
		
		
		for (int i=0; i< args.length; i++)
		{
			if((i & 1) == 0)
			{
				System.out.print("{"+args[i]+"}");
			}else
			{
				if(i>0 && args[i-1].equalsIgnoreCase("--p"))
					System.out.println(":{*******}");					
				else
					System.out.println(":{"+args[i]+"}");
			}
			
			if(i>0 && args[i-1].equalsIgnoreCase("--server"))
			{
				if(args[i]!=null && args[i].trim().equalsIgnoreCase("false"))
					params.server = false;
				else if(args[i]!=null && args[i].trim().equalsIgnoreCase("true"))
					params.server = true;
			}
		}
		System.out.println("");

		if(!printlneula(params.server))
		{
			System.out.println("You do not have permission to use this software. Please delete it from this computer");
			System.exit(-1);
		}

		String action = null;
				
		if (args.length >= 2) 
		{
			for (int i=1; i< args.length; i=i+2){
				if(args[i-1].equalsIgnoreCase("--help") || args[i-1].equalsIgnoreCase("-help") || args[i-1].equalsIgnoreCase("help"))
				{
					printUsage();
				}
				else if(args[i-1].equalsIgnoreCase("--u"))
				{
					params.username = args[i];
				}
				else if(args[i-1].equalsIgnoreCase("--p"))
				{
					params.password = args[i];
				}
				else if(args[i-1].equalsIgnoreCase("--sessionId"))
				{
					params.sessionId = args[i];
				}
				else if(args[i-1].equalsIgnoreCase("--token"))
				{
					params.token = args[i];
				}
				else if(args[i-1].equalsIgnoreCase("--jksFile"))
				{
					params.jksFile = args[i];
				}
				else if(args[i-1].equalsIgnoreCase("--jksPassword"))
				{
					params.jksPassword = args[i];
				}
				else if(args[i-1].equalsIgnoreCase("--clientId"))
				{
					params.clientId = args[i];
				}
				else if(args[i-1].equalsIgnoreCase("--endpoint"))
				{
					params.endpoint = args[i];
				}
				else if(args[i-1].equalsIgnoreCase("--action"))
				{
					action = args[i];
				}
				else if(args[i-1].equalsIgnoreCase("--operation"))
				{
					if(args[i]!=null)
					{
						if(args[i].equalsIgnoreCase("overwrite"))
						{
							params.Operation = args[i];
						}else if(args[i].equalsIgnoreCase("upsert"))
						{
							params.Operation = args[i];
						}else if(args[i].equalsIgnoreCase("append"))
						{
							params.Operation = args[i];
						}else if(args[i].equalsIgnoreCase("delete"))
						{
							params.Operation = args[i];							
						}else
						{
							System.out.println("Invalid Operation {"+args[i]+"} Must be Overwrite or Upsert or Append or Delete");
							System.exit(-1);
						}
					}
				}
			else if(args[i-1].equalsIgnoreCase("--notificationlevel"))
			{
				if(args[i]!=null)
				{
					if(args[i].equalsIgnoreCase("always"))
					{
						params.notificationLevel = args[i];
					}else if(args[i].equalsIgnoreCase("failures"))
					{
						params.notificationLevel = args[i];
					}else if(args[i].equalsIgnoreCase("warnings"))
					{
						params.notificationLevel = args[i];
					}else if(args[i].equalsIgnoreCase("never"))
					{
						params.notificationLevel = args[i];							
					}else
					{
						System.out.println("Invalid notificationLevel {"+args[i]+"} Must be 'Always' or 'Failures' or 'Warnings' or 'Never'");
						System.exit(-1);
					}
				}
			}
			else if(args[i-1].equalsIgnoreCase("--notificationemail"))
			{
				if(args[i]!=null && !args[i].trim().isEmpty())
					params.notificationEmail = args[i];
			}
			else if (args[i - 1].equalsIgnoreCase("--debug")) 
			{
					params.debug = true;
					DatasetUtilConstants.debug = true;
			}
			else if(args[i-1].equalsIgnoreCase("--ext"))
			{
					DatasetUtilConstants.ext = true;
			}
			else if(args[i-1].equalsIgnoreCase("--inputFile"))
			{
					String tmp = args[i];
					if(tmp!=null)
					{
					   File tempFile = new File (tmp);
					   if(tempFile.exists())
					   {
						   params.inputFile =   tempFile.toString();
					   }else
					   {
						   System.out.println("File {"+args[i]+"} does not exist");
						   System.exit(-1);
					   }
					}
			}
			else if(args[i-1].equalsIgnoreCase("--schemaFile"))
			{
					String tmp = args[i];
					if(tmp!=null)
					{
					   File tempFile = new File (tmp);
					   if(tempFile.exists())
					   {
						   params.schemaFile = tempFile.toString();
					   }else
					   {
						   System.out.println("File {"+args[i]+"} does not exist");
						   System.exit(-1);
					   }
					}
			}
			else if(args[i-1].equalsIgnoreCase("--dataset"))
			{
					params.dataset = args[i];
			}
			else if(args[i-1].equalsIgnoreCase("--datasetLabel"))
			{
					params.datasetLabel = args[i];
			}
			else if(args[i-1].equalsIgnoreCase("--app"))
			{
					params.app = args[i];
			}
			else if(args[i-1].equalsIgnoreCase("--useBulkAPI"))
			{
					if(args[i]!=null && args[i].trim().equalsIgnoreCase("true"))
						params.useBulkAPI = true;
			}
			else if(args[i-1].equalsIgnoreCase("--uploadFormat"))
			{
					if(args[i]!=null && args[i].trim().equalsIgnoreCase("csv"))
						params.uploadFormat = "csv";
					else if(args[i]!=null && args[i].trim().equalsIgnoreCase("binary"))
						params.uploadFormat = "binary";
				}
				else if(args[i-1].equalsIgnoreCase("--rowLimit"))
				{
					if(args[i]!=null && !args[i].trim().isEmpty())
						params.rowLimit = (new BigDecimal(args[i].trim())).intValue();
				}
				else if(args[i-1].equalsIgnoreCase("--chunkMulti"))
				{
					if(args[i]!=null && !args[i].trim().isEmpty())
						params.chunkSizeMulti = (new BigDecimal(args[i].trim())).intValue();
				}
				else if(args[i-1].equalsIgnoreCase("--rootObject"))
				{
					params.rootObject = args[i];
				}
				else if(args[i-1].equalsIgnoreCase("--fileEncoding"))
				{
					params.fileEncoding = args[i];
				}
				else if(args[i-1].equalsIgnoreCase("--server"))
				{
					if(args[i]!=null && args[i].trim().equalsIgnoreCase("true"))
						params.server = true;
					else if(args[i]!=null && args[i].trim().equalsIgnoreCase("false"))
						params.server = false;
				}
				else if(args[i-1].equalsIgnoreCase("--codingErrorAction"))
				{
					if(args[i]!=null)
					{
						if(args[i].equalsIgnoreCase("IGNORE"))
						{
							params.codingErrorAction = CodingErrorAction.IGNORE;
						}else if(args[i].equalsIgnoreCase("REPORT"))
						{
							params.codingErrorAction = CodingErrorAction.REPORT;
						}else if(args[i].equalsIgnoreCase("REPLACE"))
						{
							params.codingErrorAction = CodingErrorAction.REPLACE;
						}
						DatasetUtilConstants.codingErrorAction = params.codingErrorAction;
					}
				}
				else if(args[i-1].equalsIgnoreCase("--mode"))
				{
					String arg = args[i];
					if(arg!=null)
					{
						if (arg.equalsIgnoreCase(INCREMENTAL_MODE_INCREMENTAL)) {
							params.mode = INCREMENTAL_MODE_INCREMENTAL;
						} else if (arg.equalsIgnoreCase(INCREMENTAL_MODE_NONE)) {
							params.mode = INCREMENTAL_MODE_NONE;
						}else {
							System.out.println("Invalid mode {"+arg+"} Must be '" + INCREMENTAL_MODE_INCREMENTAL + "' or '" + INCREMENTAL_MODE_NONE + "'");
							System.exit(-1);
						}
					}
				}
				else
				{
					printUsage();
					System.out.println("\nERROR: Invalid argument: "+args[i-1]);
					System.exit(-1);
				}
			}//end for
			
			if(params.username != null)
			{
				if(params.endpoint == null || params.endpoint.isEmpty())
				{
						params.endpoint = DatasetUtilConstants.defaultEndpoint;
				}
			}
		}
		

		if(params.server)
		{
			DatasetUtilConstants.server = true;
			System.out.println();
			System.out.println("\n*******************************************************************************");					
	        try {
		        DatasetUtilServer datasetUtilServer = new DatasetUtilServer();
				datasetUtilServer.init(args, true);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("Server ended, exiting JVM.....");
			System.out.println("*******************************************************************************\n");	
			System.out.println("QUITAPP");
			System.exit(0); 
		}

		if(params.sessionId==null)
		{
			if(params.username == null || params.username.trim().isEmpty())
			{
				params.username = getInputFromUser("Enter salesforce username: ", true, false);						
			}
			
			if(params.username.equals("-1"))
			{
				params.sessionId = getInputFromUser("Enter salesforce sessionId: ", true, false);
				params.username = null;
				params.password = null;
			}else
			{
				if((params.password == null  || params.password.trim().isEmpty())&&params.jksFile==null)
				{
					params.password = getInputFromUser("Enter salesforce password: ", true, true);						
				}
			}
		}
		
		
		if(params.sessionId != null && !params.sessionId.isEmpty())
		{
			while(params.endpoint == null || params.endpoint.trim().isEmpty())
			{
				params.endpoint = getInputFromUser("Enter salesforce instance url: ", true, false);
				if(params.endpoint==null || params.endpoint.trim().isEmpty())
					System.out.println("\nERROR: endpoint must be specified when sessionId is specified");
			}
				
			while(params.endpoint.toLowerCase().contains("login.salesforce.com") || params.endpoint.toLowerCase().contains("test.salesforce.com") || params.endpoint.toLowerCase().contains("test") || params.endpoint.toLowerCase().contains("prod") || params.endpoint.toLowerCase().contains("sandbox"))
			{
				System.out.println("\nERROR: endpoint must be the actual serviceURL and not the login url");
				params.endpoint = getInputFromUser("Enter salesforce instance url: ", true, false);
			}
		}else
		{
			if(params.endpoint == null || params.endpoint.isEmpty())
			{
				params.endpoint = getInputFromUser("Enter salesforce instance url (default=prod): ", false, false);
				if(params.endpoint == null || params.endpoint.trim().isEmpty())
				{
					params.endpoint = DatasetUtilConstants.defaultEndpoint;
				}
			}
		}
		
			try 
			{
				if(params.endpoint.equalsIgnoreCase("PROD") || params.endpoint.equalsIgnoreCase("PRODUCTION"))
				{
					params.endpoint = DatasetUtilConstants.defaultEndpoint;
				}else if(params.endpoint.equalsIgnoreCase("TEST") || params.endpoint.equalsIgnoreCase("SANDBOX"))
				{
					params.endpoint = DatasetUtilConstants.defaultEndpoint.replace("login", "test");
				}
				
				URL uri = new URL(params.endpoint);
				String protocol = uri.getProtocol();
				String host = uri.getHost();
				if(protocol == null || !protocol.equalsIgnoreCase("https"))
				{
					if(host == null || !(host.toLowerCase().endsWith("internal.salesforce.com") || host.toLowerCase().endsWith("localhost")))
					{
						System.out.println("\nERROR: Invalid endpoint. UNSUPPORTED_CLIENT: HTTPS Required in endpoint");
						System.exit(-1);
					}
				}
				
				if(uri.getPath() == null || uri.getPath().isEmpty() || uri.getPath().equals("/"))
				{
					uri = new URL(uri.getProtocol(), uri.getHost(), uri.getPort(), DatasetUtilConstants.defaultSoapEndPointPath); 
				}
				params.endpoint = uri.toString();
			} catch (MalformedURLException e) {
				e.printStackTrace();
				System.out.println("\nERROR: endpoint is not a valid URL");
				System.exit(-1);
			}

		
		PartnerConnection partnerConnection = null;
		if(params.username!=null || params.sessionId != null)
		{
			try {
				partnerConnection  = DatasetUtils.login(0, params.username, params.password, params.token, params.jksFile,params.jksPassword, params.clientId,params.endpoint, params.sessionId, params.debug);
			} catch (ConnectionException e) {
				e.printStackTrace();
				System.exit(-1);
			} catch (MalformedURLException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}

		if(args.length==0 || action == null)
		{
			

			while(true)
			{
				action = getActionFromUser();
				if(action==null || action.isEmpty())
				{
					System.exit(-1);
				}
				params = new DatasetUtilParams();
				getRequiredParams(action, partnerConnection, params);
				@SuppressWarnings("unused")
				boolean status = doAction(action, partnerConnection, params);

			}
		}else
		{
			boolean status = doAction(action, partnerConnection, params);
			if(!status)
			{
				System.exit(-1);
			}
		}		
	}


	public static void printUsage()
	{
		System.out.println("\n*******************************************************************************");					
		System.out.println("Usage:");
		System.out.print("java -jar datasetutil.jar --action load --u userName --p password ");
		System.out.println("--dataset datasetAlias --inputFile inputFile --endpoint endPoint");
		System.out.println("--action  : load,defineExtractFlow,defineAugmentFlow,downloadxmd,uploadxmd,detectEncoding");
		System.out.println("          : Use load for loading csv, defineAugmentFlow for augmenting existing dataset");
		System.out.println("--u       : Salesforce.com login");
		System.out.println("--p       : (Optional) Salesforce.com password,if omitted you will be prompted");
		System.out.println("--token   : (Optional) Salesforce.com token");
		System.out.println("--endpoint: (Optional) The salesforce soap api endpoint (test/prod)");
		System.out.println("          : Default: https://login.salesforce.com/services/Soap/u/31.0");
		System.out.println("--dataset : (Optional) the dataset alias. required if action=load");
		System.out.println("--app     : (Optional) the app name for the dataset");
		System.out.println("--inputFile : (Optional) the input csv file. required if action=load");
		System.out.println("--rootObject: (Optional) the root SObject for the defineExtractFlow");
		System.out.println("--rowLimit: (Optional) the number of rows to defineExtractFlow, -1=all, deafult=1000");
		System.out.println("--sessionId : (Optional) the salesforce sessionId. if specified,specify endpoint");
		System.out.println("--fileEncoding : (Optional) the encoding of the inputFile default UTF-8");
		System.out.println("--uploadFormat : (Optional) the whether to upload as binary or csv. default binary");
		System.out.println("--mode  : (Optional) Incremental or None, default is None");

		System.out.println("*******************************************************************************\n");
		System.out.println("Usage Example 1: Upload a csv to a dataset");
		System.out.println("java -jar datasetutil.jar --action load --u pgupta@force.com --p @#@#@# --inputFile Opportunity.csv --dataset test");
		System.out.println("");
		System.out.println("Usage Example 2: Append a csv to a dataset");
		System.out.println("java -jar datasetutil.jar --action load --operation append --u pgupta@force.com --p @#@#@# --inputFile Opportunity.csv --dataset test");
		System.out.println("");
		System.out.println("Usage Example 3: Download dataset xmd files");
		System.out.println("java -jar datasetloader.jar --action downloadxmd --u pgupta@force.com --p @#@#@# --dataset test");
		System.out.println("");
		System.out.println("Usage Example 4: Upload user.xmd.json");
		System.out.println("java -jar datasetutil.jar --action uploadxmd --u pgupta@force.com --p @#@#@# --inputFile user.xmd.json --dataset test");
		System.out.println("");
		System.out.println("Usage Example 5: Generate the schema file from CSV");
		System.out.println("java -jar datasetutil.jar --action load --inputFile Opportunity.csv");
		System.out.println("");
	}
	
	static boolean printlneula(boolean server)
	{
		try
		{
			String userHome = System.getProperty("user.home");
			File lic = new File(userHome, ".ac.datautils.lic");
			if(!lic.exists())
			{
				if(!server)
				{
					System.out.println(eula);
					System.out.println();
					while(true)
					{
						String response = DatasetUtils.readInputFromConsole("Do you agree to the above license agreement (Yes/No): ");
						if(response!=null && (response.equalsIgnoreCase("yes") || response.equalsIgnoreCase("y")))
						{
							FileUtils.writeStringToFile(lic, eula);
							return true;
						}else if(response!=null && (response.equalsIgnoreCase("no") || response.equalsIgnoreCase("n")))
						{
							return false;
						}
					}
				}else
				{
					FileUtils.writeStringToFile(lic, eula);
					return true;
				}
			}else
			{
				return true;
			}
		}catch(Throwable t)
		{
			t.printStackTrace();
		}
		return false;
	}
	
	public static String eula = "/*\n" 
 +"* Copyright (c) 2014, salesforce.com, inc.\n"
 +"* All rights reserved.\n"
 +"*\n" 
 +"* Redistribution and use in source and binary forms, with or without modification, are permitted provided\n" 
 +"* that the following conditions are met:\n"
 +"* \n"
 +"*    Redistributions of source code must retain the above copyright notice, this list of conditions and the \n"
 +"*    following disclaimer.\n"
 +"*  \n"
 +"*    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and \n"
 +"*    the following disclaimer in the documentation and/or other materials provided with the distribution. \n"
 +"*    \n"
 +"*    Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or \n"
 +"*    promote products derived from this software without specific prior written permission."
 +"* \n"
 +"* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND ANY EXPRESS OR IMPLIED \n"
 +"* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A \n"
 +"* PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR \n"
 +"* ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED \n"
 +"* TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) \n"
 +"* HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING \n"
 +"* NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE \n"
 +"* POSSIBILITY OF SUCH DAMAGE. \n"
 +"*/\n";
	
	public static String getInputFromUser(String message, boolean isRequired, boolean isPassword)
	{
		String input = null;
		while(true)
		{
			try
			{
				if(!isPassword)
					input = DatasetUtils.readInputFromConsole(message);
				else
					input = DatasetUtils.readPasswordFromConsole(message);
				
				if(input==null || input.isEmpty())
				{
					if(!isRequired)
						break;
				}else
				{
					break;
				}
			}catch(Throwable me)
			{				
				input = null;
			}
		}
		return input;
	}
	
	public static String getActionFromUser()
	{
		System.out.println();
		String selectedAction = "load";
	    DecimalFormat df = new DecimalFormat("00");
	    df.setMinimumIntegerDigits(2);
		int cnt = 1;
			for(String[] action:validActions)
			{
				if(cnt==1)
					System.out.println("Available Datasetutil Actions: ");
				System.out.print(" ");
				System.out.println(DatasetUtils.padLeft(cnt+"",3)+". "+action[1]);
				cnt++;
			}
			System.out.println();
	
			while(true)
			{
				try
				{
					String tmp = DatasetUtils.readInputFromConsole("Enter Action number (0  = Exit): ");
					if(tmp==null)
						return null;
					if(tmp.trim().isEmpty())
						continue;
					long choice = Long.parseLong(tmp.trim());
					if(choice==0)
						return null; 
					cnt = 1;
					if(choice>0 && choice <= validActions.length)
					{
						for(String[] action:validActions)
						{
							if(cnt==choice)
							{
								selectedAction = action[0];
								return selectedAction;
							}
							cnt++;
						}
					}
				}catch(Throwable me)
				{				
				}
			}
		}
	
	public static boolean doAction(String action, PartnerConnection partnerConnection, DatasetUtilParams params)
	{

		if(action==null)
		{
			printUsage();
			System.out.println("\nERROR: Invalid action {"+action+"}");
			return false;
		}

		if (params.inputFile!=null) 
		{
			   File tempFile = validateInputFile(params.inputFile, action);
			   if(tempFile == null)
			   {
				   System.out.println("Inputfile {"+params.inputFile+"} is not valid");
				   return false;
			   }
		}

		if(params.dataset!=null && !params.dataset.isEmpty())
		{
			if(params.datasetLabel==null)
				params.datasetLabel = params.dataset;
			String santizedDatasetName = ExternalFileSchema.createDevName(params.dataset, "Dataset", 1, false);
			if(!params.dataset.equals(santizedDatasetName))
			{
				System.out.println("\n Warning: dataset name can only contain alpha-numeric or '_', must start with alpha, and cannot end in '__c'");
				System.out.println("\n changing dataset name to: {"+santizedDatasetName+"}");
				params.dataset = santizedDatasetName;
			}
		}
		
		Charset fileCharset = null;
		if(params.fileEncoding!=null && !params.fileEncoding.trim().isEmpty() && !params.fileEncoding.trim().equalsIgnoreCase("auto"))
		{
			try
			{
					fileCharset = Charset.forName(params.fileEncoding);
			} catch (Throwable  e) {
				e.printStackTrace();
				System.out.println("\nERROR: Invalid fileEncoding {"+params.fileEncoding+"}");
				return false;
			}
		}
	
			if(action.equalsIgnoreCase("load"))
			{
				if (params.inputFile==null || params.inputFile.isEmpty()) 
				{
					System.out.println("\nERROR: inputFile must be specified");
					return false;
				}
				
				if (params.dataset==null || params.dataset.isEmpty()) 
				{
					System.out.println("\nERROR: dataset name must be specified");
					return false;
				}
				
				Session session = null;
				try {
					String orgId = null;
					orgId = partnerConnection.getUserInfo().getOrganizationId();
					session = Session.getCurrentSession(orgId, params.dataset, true);
//					session = new Session(orgId,params.dataset);
//			        ThreadContext threadContext = ThreadContext.get();
//			        threadContext.setSession(session);
			        session.start();
					try
					{
						boolean status = DatasetLoader.uploadDataset(params.inputFile, params.schemaFile, params.uploadFormat, params.codingErrorAction,fileCharset, params.dataset,
								params.app, params.datasetLabel, params.Operation, params.useBulkAPI,params.chunkSizeMulti, partnerConnection, params.notificationLevel, params.notificationEmail,
								params.mode, System.out);
						if(status)
							session.end();
						else
							session.fail("Check sessionLog for details");
						return status;
					} catch (DatasetLoaderException e) {
						session.fail(e.getMessage());
						return false;
					}
				} catch (Exception e) {
						System.out.println();
						e.printStackTrace(System.out);
						if(session!=null)
							session.fail(e.getLocalizedMessage());
						return false;
				}
			} else if(action.equalsIgnoreCase("detectEncoding"))
			{
				if (params.inputFile==null) 
				{
					System.out.println("\nERROR: inputFile must be specified");
					return false;
				}
				
				try {
					CharsetChecker.detectCharset(new File(params.inputFile),System.out);
				} catch (Exception e) {
						e.printStackTrace(System.out);
						return false;
				}
			}else if(action.equalsIgnoreCase("uploadxmd"))
				{
					if (params.inputFile==null) 
					{
						System.out.println("\nERROR: inputFile must be specified");
						return false;
					}
					
					if(params.dataset==null)
					{
						System.out.println("\nERROR: dataset must be specified");
						return false;
					}


					try {
						XmdUploader.uploadXmd(params.inputFile, params.dataset, null, null, partnerConnection);
					} catch (Exception e) {
							e.printStackTrace(System.out);
							return false;
					}
			} else if(action.equalsIgnoreCase("downloadxmd"))
			{
				if (params.dataset==null) 
				{
					System.out.println("\nERROR: dataset alias must be specified");
					return false;
				}
				
				try {
					DatasetDownloader.downloadEM(params.dataset, partnerConnection);
				} catch (Exception e) {
					e.printStackTrace(System.out);
					return false;
				}

			}else if(action.equalsIgnoreCase("downloadErrorFile"))
			{
				if (params.dataset==null) 
				{
					System.out.println("\nERROR: dataset alias must be specified");
					return false;
				}

				try {
					DataFlowMonitorUtil.getJobsAndErrorFiles(partnerConnection, params.dataset);
				} catch (Exception e) {
						System.out.println();
						e.printStackTrace(System.out);
						return false;
				}
				
			}else
			{
				printUsage();
				System.out.println("\nERROR: Invalid action {"+action+"}");
				return false;
			}
			return true;
	}
	
	
	public static void getRequiredParams(String action,PartnerConnection partnerConnection, DatasetUtilParams params)
	{
		if (action == null || action.trim().isEmpty())
		{
				System.out.println("\nERROR: Invalid action {"+action+"}");
				System.out.println();
				return;
		}else
		if(action.equalsIgnoreCase("load"))
		{
			while (params.inputFile==null || params.inputFile.isEmpty()) 
			{
				String tmp = getInputFromUser("Enter inputFile: ", true, false);
				if(tmp!=null)
				{
					   File tempFile = validateInputFile(tmp, action);
					   if(tempFile !=null)
					   {
						   params.inputFile =   tempFile.toString();
						   break;
					   }
				}else 
				 System.out.println("File {"+tmp+"} not found");
				System.out.println();
			}

			if (params.dataset==null || params.dataset.isEmpty()) 
			{
				params.dataset = getInputFromUser("Enter dataset name: ", true, false);						
			}

			if (params.datasetLabel==null || params.datasetLabel.isEmpty()) 
			{
				params.datasetLabel = getInputFromUser("Enter datasetLabel (Optional): ", false, false);						
			}

			if (params.app==null || params.app.isEmpty()) 
			{
				params.app = getInputFromUser("Enter datasetFolder (Optional): ", false, false);
				if(params.app != null && params.app.isEmpty())
					params.app = null;
			}

			while (params.Operation==null || params.Operation.isEmpty()) 
			{
				params.Operation = getInputFromUser("Enter Operation (Default=Overwrite): ", false, false);
				if(params.Operation == null || params.Operation.isEmpty())
				{
					params.Operation = "overwrite";
				}
				else
				{
					if(params.Operation.equalsIgnoreCase("overwrite"))
					{
						params.Operation = "overwrite";
					}else if(params.Operation.equalsIgnoreCase("upsert"))
					{
						params.Operation = "upsert";
					}else if(params.Operation.equalsIgnoreCase("append"))
					{
						params.Operation = "append";
					}else if(params.Operation.equalsIgnoreCase("delete"))
					{
						params.Operation = "delete";							
					}else
					{
						System.out.println("Invalid Operation {"+params.Operation+"} Must be Overwrite or Upsert or Append or Delete");
						params.Operation = null;
					}
				}

				
			}
			
			if (params.fileEncoding==null || params.fileEncoding.isEmpty()) 
			{
				while(true)
				{
					params.fileEncoding = getInputFromUser("Enter fileEncoding (Optional): ", false, false);
					if(params.fileEncoding != null && !params.fileEncoding.trim().isEmpty())
					{
						try
						{
							Charset.forName(params.fileEncoding);
							break;
						} catch (Throwable  e) {
						}
					}else
					{
						params.fileEncoding  = null;
						break;
					}
					System.out.println("\nERROR: Invalid fileEncoding {"+params.fileEncoding+"}");
					System.out.println();
				}
			}

			while (params.uploadFormat==null || params.uploadFormat.isEmpty()) 
			{
				String response = getInputFromUser("Parse file before uploading (Yes/No): ", false, false);	
				if(response==null || response.isEmpty())
				{
					params.uploadFormat = "binary";
					break;
				}else if(response!=null && !(response.equalsIgnoreCase("Y") || response.equalsIgnoreCase("YES") || response.equalsIgnoreCase("N") || response.equalsIgnoreCase("NO")))
				{
					continue;
				}else if(response.equalsIgnoreCase("Y") || response.equalsIgnoreCase("YES"))
				{ 
					params.uploadFormat = "binary";
					break;
				}else
				{
					params.uploadFormat = "csv";
					break;
				}
//				System.out.println();
			}
			while (params.mode ==null || params.mode.isEmpty())
			{
				params.mode = getInputFromUser("Enter mode: ", false, false);
				if (params.mode == null || params.mode.isEmpty()) {
					params.mode = INCREMENTAL_MODE_NONE;
				}
				else if(params.mode.equalsIgnoreCase(INCREMENTAL_MODE_INCREMENTAL))
				{
					params.mode = INCREMENTAL_MODE_INCREMENTAL;
				}
				else if (params.mode.equalsIgnoreCase(INCREMENTAL_MODE_NONE)){
					params.mode = INCREMENTAL_MODE_NONE;
				}
				else
				{
					System.out.println("Invalid mode {"+params.mode +"} Must be '" + INCREMENTAL_MODE_INCREMENTAL + "' or '" + INCREMENTAL_MODE_NONE + "'");
					params.mode = null;
				}
			}
			
		}else if(action.equalsIgnoreCase("downloadErrorFile"))
		{
			if (params.dataset==null || params.dataset.isEmpty()) 
			{
				params.dataset = getInputFromUser("Enter dataset name: ", true, false);						
			}
			
		} else if(action.equalsIgnoreCase("detectEncoding"))
		{
			while (params.inputFile==null || params.inputFile.isEmpty()) 
			{
				String tmp = getInputFromUser("Enter inputFile: ", true, false);
				if(tmp!=null)
				{
					   File tempFile = validateInputFile(tmp, action);
					   if(tempFile !=null)
					   {
						   params.inputFile =   tempFile.toString();
						   break;
					   }
				} else
					System.out.println("File {"+tmp+"} not found");
				System.out.println();
			}
				
		}else if(action.equalsIgnoreCase("uploadxmd"))
		{
			while (params.inputFile==null || params.inputFile.isEmpty()) 
			{
				String tmp = getInputFromUser("Enter inputFile: ", true, false);
				if(tmp!=null)
				{
				   File tempFile = validateInputFile(tmp, action);
				   if(tempFile !=null)
				   {
					   params.inputFile =   tempFile.toString();
					   break;
				   }
				}else
					System.out.println("File {"+tmp+"} not found");
				System.out.println();
			}

			if (params.dataset==null || params.dataset.isEmpty()) 
			{
				params.dataset = getInputFromUser("Enter dataset name: ", true, false);						
			}
		} else if(action.equalsIgnoreCase("downloadxmd"))
		{
			if (params.dataset==null || params.dataset.isEmpty()) 
			{
				params.dataset = getInputFromUser("Enter dataset name: ", true, false);						
			}
				
		}else if(action.equalsIgnoreCase("downloadErrorFile"))
		{
			if (params.dataset==null || params.dataset.isEmpty()) 
			{
				params.dataset = getInputFromUser("Enter dataset name: ", true, false);						
			}
		}else
		{
				printUsage();
				System.out.println("\nERROR: Invalid action {"+action+"}");
		}
	}
	
	public static File validateInputFile(String inputFile, String action)
	{
		File temp = null;
		if (inputFile!=null) 
		{
			temp = new File(inputFile);
			if(!temp.exists() && !temp.canRead())
			{
				System.out.println("\nERROR: inputFile {"+temp+"} not found");
				return null;
			}
			
			String ext = FilenameUtils.getExtension(temp.getName());
			if(ext == null || !(ext.equalsIgnoreCase("csv") || ext.equalsIgnoreCase("txt") || ext.equalsIgnoreCase("bin") || ext.equalsIgnoreCase("gz")  || ext.equalsIgnoreCase("json")))
			{
				System.out.println("\nERROR: inputFile does not have valid extension");
				return null;
			}
			
			if(action.equalsIgnoreCase("load"))
			{
					byte[] binHeader = new byte[5];
					if(ext.equalsIgnoreCase("bin") || ext.equalsIgnoreCase("gz"))
					{
						try {
							InputStream fis = new FileInputStream(temp);
							if(ext.equalsIgnoreCase("gz"))
								fis = new GzipCompressorInputStream(new FileInputStream(temp));
							int cnt = fis.read(binHeader);
							if(fis!=null)
							{
								IOUtils.closeQuietly(fis);
							}
							if(cnt<5)
							{
								System.out.println("\nERROR: inputFile {"+temp+"} in not valid");
								return null;
							}
						} catch (FileNotFoundException e) {
							e.printStackTrace();
							System.out.println("\nERROR: inputFile {"+temp+"} not found");
							return null;
						} catch (IOException e) {
							e.printStackTrace();
							System.out.println("\nERROR: inputFile {"+temp+"} in not valid");
							return null;
						}
					
						if(!EbinFormatWriter.isValidBin(binHeader))
						{
							if(ext.equalsIgnoreCase("bin"))
							{
								System.out.println("\nERROR: inputFile {"+temp+"} in not valid binary file");
								return null;
							}
						}
					}
			}
			
			if(ext.equalsIgnoreCase("json"))
			{
				try
				{
					ObjectMapper mapper = new ObjectMapper();	
					mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
					mapper.readValue(temp, Object.class);
				}catch(Throwable t)
				{
					System.out.println("\nERROR: inputFile {"+temp+"} is not valid json, Error: " + t.getMessage());
					return null;
				}
			}
					
		}
		return temp;
		
	}
	

	
	public static void printBanner()
	{
		for(int i=0;i<5;i++)
			System.out.println();
		System.out.println("\n\t\t**************************************************");
		System.out.println("\t\tSalesforce Analytics Cloud Dataset Utils - " + getAppversion());
		System.out.println("\t\t**************************************************\n");			
	}
	
	public static String getAppversion()
	{
        try {
            Properties versionProps = new Properties();
            versionProps.load(DatasetUtilMain.class.getClassLoader().getResourceAsStream("version.properties"));
            return versionProps.getProperty("datasetutils.version");
        } catch (Throwable t) {
            //t.printStackTrace();
        }
		return "0.0.0";
	}

    public static void printClasspath() 
    {
		System.out.println("\n*******************************************************************************");					
		System.out.println("java.version:"+System.getProperty("java.version"));
		System.out.println("java.class.path:"+System.getProperty("java.class.path"));

        System.out.println("*******************************************************************************\n");					 
    }
			
}
