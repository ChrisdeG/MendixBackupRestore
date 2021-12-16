package main.java.com.ccdg.app;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/*
 * all implemented functions of the restore tool 
 */

/**
 * @author Chris de Gelder
 *
 */
public class MendixUtil {
	private static final String MXBACKUPAPIV1 = "https://deploy.mendix.com/api/1/apps";
	// json fields in api response
	private static final String CREATED_ON = "CreatedOn";
	private static final String SNAPSHOT_ID = "SnapshotID";
	// storage keys
	private static final String MXAPIKEY = "mxapikey";
	private static final String MENDIX_API_KEY = "Mendix-ApiKey";
	private static final String MENDIX_USER_NAME = "Mendix-UserName";
	private static final String DBPASSWORD = "password";
	private static final String DBUSERNAME = "username";
	private static final String PGDIR = "pgdir"; // postgres directory
	private static final String PGPORT = "pgport"; // postgres port
	private static final String DLDIR = "donwnloaddir";
	private static final String MXAPIUSER = "mxapiuser";
	private static final String BACKUPNAMING = "backupnaming";
	private static final String SIZEPREFIX = "lastdownloadsize_";
	private static final String MRUPREFIX = "mru";
	private static final String APP_ID = "AppId";
	// symmetric key for storage
	private static final String KEY = "6GHH&Jkw2#hgfaUy";
	private static final String FILENAMETAG = "filename=";
	private static final int BUFFER_SIZE = 4096*16;	
	private static final int MAX_MRU = 6;
	// shared with settings
	String username;
	String password;
	String apiuser;
	String apikey;
	String postgresDirectory; 
	String postgresPort;
	String backupNaming;
	String downloadDirectory;
	private String postgresversion;
	private int restorelinecount;
	private List consoleList; 
	private List backuplist;
	private ProgressBar progressBar;
	private StyledText styledText;
	private JSONArray apps;
	private JSONArray backups;
	private ArrayList<String> applist;
	private Display display;
	private Process runProcess;
	private Thread downloadThread;
	private Runnable runnable;
	protected String filepath;
	private static Cursor cursor = null;


	/**
	 * Constructor. Create a MendixUtil object that does all the dirty work for you. The SWT objects are passed
	 * from the ui to show lists and progress.
	 * 	
	 * @param display		SWING display
	 * @param dbUsername	Database user name
	 * @param dbPassword	Database password
	 * @param consoleList	List on screen with console messages
	 * @param styledText	Textbox on screen
	 * @param progressBar	progressbar
	 * @param applist		application list control
	 * @param backuplist	backup list control
	 */
	public MendixUtil(Display display, String dbUsername, String dbPassword, List consoleList, StyledText styledText, ProgressBar progressBar, List applist, List backuplist) {
		this.display = display;
		this.username = dbUsername;
		this.password = dbPassword;
		this.consoleList = consoleList;
		this.styledText = styledText;
		this.progressBar = progressBar;
		this.backuplist = backuplist;
		restorePreferences(applist, null);  
		getPostgresfolder();
	}

	/**
	 * get the current version of postgres to make a path to the executables.
	 * todo: hardcoded C:\
	 * SELECT *
		FROM pg_settings
		WHERE 
		--name = 'data_directory' and 
	 */
	private void getPostgresfolder() {
		// Computer\HKEY_LOCAL_MACHINE\SOFTWARE\PostgreSQL\Installations\postgresql-x64-10
		postgresversion = getPostgresVersion();
		if (postgresversion != null) {
			boolean tryDir = tryCustomDirectory(postgresDirectory);
			if (!tryDir) {
				tryDir = tryDirectory(System.getenv("ProgramFiles") + "/PostgreSQL/");
			}
			if (!tryDir) {
				tryDir = tryDirectory(System.getenv("ProgramFiles(X86)") + "/PostgreSQL/");
			}
			if (!tryDir) {
				consoleWrite("Postgres not found " + postgresversion);
			} 
		} else {
			consoleWrite("Postgres not found, This tool only works with postgres. Please set the directory in settings.");
		}
	}

	/*
	 * Check whether the directory exists
	 **/

	private boolean tryDirectory(final String directory) {
		Path path = Paths.get(directory + postgresversion);
		while ((postgresversion.endsWith(".") || Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) && postgresversion.length()>0) {
			postgresversion = postgresversion.substring(0,  postgresversion.length()-1);
			path = Paths.get(directory + postgresversion);
		}
		if (postgresversion.length() > 0) {
			consoleWrite("Found postgres folder: " + directory + postgresversion);
			postgresDirectory = directory + postgresversion;
			return true;
		} else {
			return false;
		}
	}
	/**
	 * Try a directory without version
	 * @param directory
	 * @return
	 */

	private boolean tryCustomDirectory(final String directory) {
		if (directory != null && !directory.isEmpty()) {
			Path path = Paths.get(directory);
			if (Files.isDirectory(path)) {
				consoleWrite("Found postgres folder: " +directory);
				postgresDirectory = directory;
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	/**
	 * get settings from preferences
	 *  
	 * @param list SWT List of applications  
	 */
	public void restorePreferences(List list, String filter) {
		Preferences prefs = getPreferences();

		// second parameter is default value
		apiuser = prefs.get(MXAPIUSER, ""); 
		username = prefs.get(DBUSERNAME, "postgres"); 
		password = prefs.get(DBPASSWORD, "postgres"); 
		postgresDirectory = prefs.get(PGDIR, postgresDirectory);
		postgresPort = prefs.get(PGPORT, "5432");
		backupNaming = prefs.get(BACKUPNAMING, "_{environment}_{date}");
		String home = System.getProperty("user.home") + File.separator + "downloads";
		downloadDirectory = prefs.get(DLDIR, home);
		try {
			apikey = decryptString(KEY, prefs.get(MXAPIKEY, "")); 
		} catch (Exception e) {
			consoleWrite(e.getMessage());
		}
		loadApps();
		parseJsonAndSortToList(list, filter);
	}
	/**
	 * store the Apps JSON in a file for convenience. Next time no wait for apps.
	 * 
	 * @param apps JSON formatted list of apps
	 */
	private void storeApps(String apps) {
		String FileName = localStorage();
		if (FileName != null) {
			try{
				FileWriter fw = new FileWriter(FileName);
				BufferedWriter bw = new BufferedWriter(fw);
				bw.write(apps);
				bw.close();
			}
			catch (IOException e){
				consoleWrite(e.getMessage());
			}
		}
	}
	/**
	 * Load the apps from the previous storage (local cache)
	 */
	private void loadApps() {
		String FileName = localStorage();
		if (FileName != null) {
			String ret = "";

			try {
				File file = new File(FileName);
				if (file.exists()) {
					InputStream inputStream = new FileInputStream(file);

					if ( inputStream != null ) {
						InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
						BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
						String receiveString = "";
						StringBuilder stringBuilder = new StringBuilder();

						while ( (receiveString = bufferedReader.readLine()) != null ) {
							stringBuilder.append(receiveString);
						}

						inputStream.close();
						ret = stringBuilder.toString();
						if (ret != null && !ret.isEmpty()) {
							this.apps = new JSONArray(ret);
						}
					}
				}
			} catch (FileNotFoundException e) {
				consoleWrite("File not found: " + e.getMessage());
			} catch (IOException e) {
				consoleWrite("Can not read file: " + e.getMessage());
			}
		}
	}

	/**
	 * @return /users/NAME/roaming/appdata/Mendixtools/apps.txt
	 */
	private String localStorage(){
		String appData = System.getenv("AppData");
		if (appData != null && !appData.isEmpty()) {
			String directoryName = appData.concat("/MendixTools");
			File directory = new File(directoryName);
			if (! directory.exists()){
				directory.mkdir();
			}
			return directoryName + "/" + "apps.txt";   
		} else {
			return null;
		}
	}
	/**
	 * set the api user and key and store in prefs
	 * 
	 * @param apiUser Username
	 * @param apiKey ApiKey
	 * @param userName postgres username
	 * @param password postgres password
	 * @param postgresDirectory postgres directory
	 * @param postgresPort		postgres port (default 5432)
	 * @param downloadDirectory 
	 */
	public void setUserAndKey(String apiUser, String apiKey, String userName, String password, String postgresDirectory, String postgresPort, String downloadDirectory) {
		this.apiuser = apiUser;
		this.apikey = apiKey;
		this.username = userName;
		this.password = password;
		this.postgresDirectory = postgresDirectory;
		this.postgresPort = postgresPort;
		this.downloadDirectory = downloadDirectory;
		Preferences prefs = getPreferences();

		prefs.put(MXAPIUSER, apiUser);
		prefs.put(DBUSERNAME, userName);
		prefs.put(DBPASSWORD, password);
		prefs.put(PGDIR, postgresDirectory);
		prefs.put(PGPORT, postgresPort);
		prefs.put(DLDIR, downloadDirectory);
		try {
			prefs.put(MXAPIKEY, encryptString(KEY, apikey));
		} catch (Exception e) {
			consoleWrite("Error storing api username and key: " +e.getMessage());
		}
		// after the setting has changed try postgres again.
		getPostgresfolder();
	}
	/**
	 * Store the setting
	 * @param backupNaming string with tokens {environment} and {date}
	 */
	public void setBackupNaming(String backupNaming) {
		Preferences prefs = getPreferences();
		this.backupNaming = backupNaming;
		prefs.put(BACKUPNAMING, backupNaming);
	}
	/**
	 * get version of postgres in form 9.4.18
	 * 
	 * @return version
	 */
	public String getPostgresVersion() {
		String result = null;
		try {
			Class.forName("org.postgresql.Driver");
			Connection connection = DriverManager.getConnection(
					"jdbc:postgresql://127.0.0.1:"+this.postgresPort+"/postgres",
					username,
					password);
			try {
				DatabaseMetaData metadata = connection.getMetaData();
				return metadata.getDatabaseProductVersion();
			} finally {
				connection.close();
			}
		} catch (Exception e) {
			consoleWrite("Error connecting with postgres " + e.getMessage());
		}
		return result;
	}
	/**
	 * Create a database name according to naming convention
	 * if database exists a suffix _1, _2 is added. All databasenames are unique
	 * 
	 * @param appid			application id
	 * @param environment	environment like production, acceptance	
	 * @param createdOn		Date of the backup
	 * @return	Unique databasename
	 */
	public String getTargetDatabaseName(String appid, String environment, Date createdOn) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");  
		String strDate= formatter.format(createdOn);  
		String dbName = appid + backupNaming.replace("{environment}", environment).replace("{date}", strDate);
		String ext = getNonExistingDatabaseExt(dbName);
		return dbName + ext;
	}
	/**
	 * create a new uniquely extension database like _21 for myproject_21
	 * 
	 * @param databasename name of the database
	 * @return unique database extension, will never overwrite an existing one
	 */
	public String getNonExistingDatabaseExt(String databasename) {
		String result = null;
		try {
			int suffixnumber = 0;
			Class.forName("org.postgresql.Driver");
			Connection connection = DriverManager.getConnection(
					"jdbc:postgresql://127.0.0.1:"+this.postgresPort+"/postgres",
					username,
					password);
			try {
				// SELECT datname FROM pg_catalog.pg_database WHERE lower(datname) = 'project_21'
				Statement statement = connection.createStatement();
				while (suffixnumber < 1000) {
					String suffix = (suffixnumber > 0) ? "_" + suffixnumber : ""; 
					ResultSet set = statement.executeQuery("SELECT count(datname) FROM pg_catalog.pg_database WHERE lower(datname) = '" + databasename.toLowerCase() + suffix + "'");
					Long count = 0L;
					if (set.next()) {
						count = set.getLong(1);
					}
					if (count == 0L) {
						return suffix;
					}
					suffixnumber++;
				}
			} finally {
				connection.close();
			}
		} catch (Exception e) {
			consoleWrite("Error connecting with postgres " + e.getMessage());
			e.printStackTrace();
		}
		return result;
	}
	/**
	 * @param timeout 
	 * 
	 */
	public OkHttpClient getClient(int timeout) {
		int finalTimeOut = 60;
		if (timeout > 10) {
			finalTimeOut = timeout;
		}
		return new OkHttpClient.Builder()
				.readTimeout(finalTimeOut, TimeUnit.SECONDS)
				.build();
	}

	/**
	 * Call the Mendix Api and get a list of app you have access to
	 * 
	 * @param list UI control that contains the list
	 */
	public void GetAppList(List list, String filter) {

		OkHttpClient client = getClient(60);

		Request request = new Request.Builder()
				.url(MXBACKUPAPIV1)
				.get()
				.addHeader(MENDIX_USER_NAME, apiuser)
				.addHeader(MENDIX_API_KEY, apikey)
				.build();

		try {
			Response response = client.newCall(request).execute();
			if (response.code() == 200) {
				String appsJSON = response.body().string();
				storeApps(appsJSON);
				consoleWrite(appsJSON);
				this.apps = new JSONArray(appsJSON);	
				parseJsonAndSortToList(list, filter);
			} else {
				backuplist.removeAll();
				if (response.code() == 401) {
					consoleWrite("Unauthorized, Check your username and api-key ");
				} else {
					consoleWrite("Error getting apps: "  + response.code());
				}
			}
		} catch (Exception e) {
			backuplist.removeAll();
			consoleWrite("Exception gettings apps: " + e.getMessage());
		}

	}
	public void FilterAppList(List applist2, String filter) {
		GetAppList(applist2, filter);
	}


	public void getEnvironmentList(int selectionCount, List environmentlist){
		if (selectionCount >= 0) {
			OkHttpClient client = getClient(60);

			String appid = apps.getJSONObject(AppIndexByListIndex(selectionCount)).getString(APP_ID);

			Request request = new Request.Builder()
					.url(MXBACKUPAPIV1 + "/" + appid +"/environments")
					.get()
					.addHeader(MENDIX_USER_NAME, apiuser)
					.addHeader(MENDIX_API_KEY, apikey)
					.build();

			try {
				Response response = client.newCall(request).execute();
				if (response.code() == 200) {

					String appsJSON = response.body().string();
					JSONArray environments = new JSONArray(appsJSON);
					environmentlist.removeAll();
					ArrayList<String> sorted = new ArrayList<String>();
					for (int i = 0; i < environments.length(); i++) {
						String mode = environments.getJSONObject(i).getString("Mode");
						if (mode.toLowerCase().contentEquals("sandbox")) {
							consoleWrite(appid + " is a sandbox");
						} else {
							sorted.add(mode);
						}
					}
					Collections.sort(sorted);
					environmentlist.setItems(sorted.toArray(new String[0]));
				}
			} catch (IOException e) {
				consoleWrite("Error getting environments" + e.getMessage());
			}
		}
	}

	/**
	 * Parse the json from the applist and do a primitive sort
	 * 
	 * @param list User interface List 
	 * @param filter 
	 */
	private void parseJsonAndSortToList(List list, String filter) {
		list.removeAll();
		boolean skip;
		if (this.apps != null) {
			this.applist = new ArrayList<String>();
			for (int i = 0; i < apps.length(); i++) {
				skip = false;
				// filter data
				if (filter != null && !filter.isEmpty() && !apps.getJSONObject(i).getString("Name").toLowerCase().contains(filter.toLowerCase())) {
					skip = true;
				} 
				// skip sandbox
				if (apps.getJSONObject(i).has("Url")) {
					if (apps.getJSONObject(i).getString("Url").contains("mxapps.io")) {
						skip = true;
					}
				} else {
					// skip mobile projects
					skip = true;
				}
				if (!skip) {
					applist.add(apps.getJSONObject(i).getString("Name"));
				}
			}
			Collections.sort(applist);
			list.setItems(applist.toArray(new String[0]));
		}
	}

	/**
	 * Get the application id based on application name
	 * @param appName Application name
	 * @return either application or empty string;
	 */
	public String AppIdByAppName(String appName) {
		for (int i = 0; i < apps.length(); i++) {
			if (apps.getJSONObject(i).getString("Name").contentEquals(appName)){
				return (apps.getJSONObject(i).getString(APP_ID));
			}
		}
		return "";
	}
	/**
	 * the applist is sorted, get the id of the original json app list.
	 * listindex is the index from the applist on screen.
	 * 
	 * @param listindex
	 * @return
	 */
	private int AppIndexByListIndex(int listindex) {
		if (listindex >= 0 && listindex < applist.size()) {
			String appName = applist.get(listindex);
			for (int i = 0; i < apps.length(); i++) {
				if (apps.getJSONObject(i).getString("Name").contentEquals(appName)){
					return i;
				}
			}
		}
		return -1;
	}
	/**
	 * Get the selectionIndex of the appname 
	 * 
	 * @param appName application name
	 * @return application index
	 */
	public int AppIndexByAppName(String appName) {
		for (int i = 0; i < apps.length(); i++) {
			if (apps.getJSONObject(i).getString("Name").contentEquals(appName)){
				return (i);
			}
		}
		return -1;
	}
	/**
	 * Call mendix to retrieve a list of available backups
	 *  
	 * @param selectionCount selected line of list
	 * @param environment selected environment (production, acceptance, test)
	 * return true is list has at least one item.
	 */
	public boolean GetBackupList(int selectionCount, String environment ) {
		if (selectionCount >= 0) {
			int appIndex = AppIndexByListIndex(selectionCount);
			String appid = apps.getJSONObject(appIndex).getString(APP_ID);
			addMRU(appIndex);
			OkHttpClient client = getClient(60);

			Request request = new Request.Builder()
					.url(MXBACKUPAPIV1+"/"+appid+"/environments/"+environment+"/snapshots")
					.get()
					.addHeader(MENDIX_USER_NAME, apiuser)
					.addHeader(MENDIX_API_KEY, apikey)
					.build();
			try {
				Response response = client.newCall(request).execute();
				String result = response.body().string();
				backuplist.removeAll();
				if (response.isSuccessful()) {
					consoleWrite(result);
					this.backups = new JSONArray(result);	
					SimpleDateFormat dt1 = new SimpleDateFormat("yyyy-MM-dd HH:mm");
					SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
					for (int i = 0; i < backups.length(); i++) {
						// if a backup is being created createdon is null
						if (!backups.getJSONObject(i).isNull(CREATED_ON)) {
							backuplist.add(dt1.format(new Date(backups.getJSONObject(i).getLong(CREATED_ON))));
							//backuplist.add(dt1.format(parser.parse(backups.getJSONObject(i).getString(CREATED_ON))));
						}
					}
					return backups.length() > 0;
				} else {
					consoleWrite("Error getting backuplist");
					consoleWrite(result);
					return false;
				}
			} catch (Exception e) {
				consoleWrite("Error getting list " + e.getMessage());
				e.printStackTrace();
			}
		} else {
			return false;
		}
		return false;
	}
	/**
	 * Retrieve the backup link of the OnlyDatabase from the json and download the file into downloads
	 * 
	 * @param selectedAppIndex	Selected App
	 * @param selectedBackupIndex	Selected backup
	 * @param backuplist	List of backups
	 * @param environment	environment like production, acceptance
	 * @param doDownload	if true downloads the file
	 * @param doRestore		if true restores the file
	 * @param includeDocuments 
	 */
	public void GetBackupLinkDownloadAndRestore(final int selectedAppIndex, final int selectedBackupIndex, List backuplist,  final String environment, final boolean doDownload, boolean doRestore, final boolean includeDocuments) {
		String postgresPort = this.postgresPort;
		this.runnable = new Runnable() {
			public void run()
			{		
				String dbname = "";
				String filename = "";
				if (environment == null || environment.isEmpty()) {
					consoleWrite("Select a valid environment");
					return;
				}
				if (apps != null) {
					String appid = "";
					String backupid = "";
					//Date createdOn = null;
					Long createdOn = 0L;
					try {
						appid = apps.getJSONObject(AppIndexByListIndex(selectedAppIndex)).getString(APP_ID);
						backupid = backups.getJSONObject(selectedBackupIndex).getString(SNAPSHOT_ID);
						// if a backup is being created createdon is null
						if (!backups.getJSONObject(selectedBackupIndex).isNull(CREATED_ON)) {
							//createdOn = parseJSONDate(backups.getJSONObject(selectedBackupIndex).getString(CREATED_ON));
							createdOn = backups.getJSONObject(selectedBackupIndex).getLong(CREATED_ON);

						}
					} catch (Exception exp) {
						consoleWrite("Error getting list " + exp.getMessage());
					}
					OkHttpClient client = getClient(60);

					Request request = new Request.Builder()
							.url(MXBACKUPAPIV1+"/"+appid+"/environments/"+environment+"/snapshots/"+backupid)
							.get()
							.addHeader(MENDIX_USER_NAME, apiuser)
							.addHeader(MENDIX_API_KEY, apikey)
							.build();

					try {
						Response response = client.newCall(request).execute();
						String result = response.body().string();
						if (response.isSuccessful()) {
							String scope = includeDocuments ? "DatabaseAndFiles" : "DatabaseOnly";
							String url = new JSONObject(result).getString(scope);	
							String saveDir = downloadDirectory;
							filename = downloadFile(url, appid, environment, saveDir, doDownload, includeDocuments);
						} else {
							consoleWrite("Error getting backup link");
							consoleWrite(result);
						}
					} catch (IOException exp) {
						consoleWrite("Error getting list " + exp.getMessage());
					}
					if (filename.isEmpty()) {
						return;
					}
					dbname = appid + backupid;
					dbname = getTargetDatabaseName(appid, environment, new Date(createdOn));
				}
				if (doRestore) {
					try {
						String dbFilename = filename;
						String extractDirectory = null;
						if (includeDocuments) {
							// todo get extract dir - move file to target directory 
							extractDirectory = extractTarGz(filename);
							dbFilename = extractDirectory + File.separator +"db" + File.separator + "db.backup";
						}
						// create the database
						//String commandcreate = "\"C:/Program Files/PostgreSQL/"+postgresversion+"/bin/psql.exe\" --username \"" + username + "\" --no-password --dbname postgres --command \"Create Database \""+dbname+"\"\"";			
						String commandcreate = "\""+postgresDirectory +"/bin/psql.exe\" --port " + postgresPort + " --username \"" + username + "\" --no-password --dbname postgres --command \"Create Database \"\""+dbname+"\"\"\"";
						int exitCode = runCommand(commandcreate, false);
						if (exitCode == 0) {
							consoleWrite("database created");
						} else {
							return;
						}
						// count the contents ( only for progressbar)
						restorelinecount = 0;
						String commandlist = "\""+postgresDirectory +"/bin/pg_restore.exe\" -l \"" + dbFilename+ "\"";
						runCommand(commandlist, true);
						// restore the database 
						setStyledText("Restoring database");
						String command = "\""+postgresDirectory +"/bin/pg_restore.exe\" --host localhost --port " + postgresPort + " --jobs 2 --username \"" + username + "\" --dbname \""+dbname+"\" --no-owner --no-password  --verbose \"" + dbFilename+ "\"";
						int exitCode2 = runCommand(command, false);
						if (exitCode2 == 0) {
							consoleWrite("Database restored as " + dbname);
							if (includeDocuments) {
								consoleWrite("Set your 'UploadedFilesPath' in Mendix to ");
								consoleWrite(extractDirectory + File.separator + "tree");
								consoleWrite("https://docs.mendix.com/refguide/custom-settings");
								consoleWrite(" Environments > Environment Details > Runtime > Custom Runtime Settings");
							}
						} else {
							consoleWrite("Restore returned errorcode but the database may as well be restored.");
							consoleWrite("This may happen when a postgres 9 database is restored to 10 or later ");
						}
						setProgress(0);
						setStyledText(dbname);
					} catch (IOException ioEx) {
						consoleWrite(ioEx.getMessage());
					} catch (InterruptedException intEx) {
						consoleWrite(intEx.getMessage());
					}
				}
			}
		};
		downloadThread = new Thread(this.runnable);		
		downloadThread.start(); 
	}

	protected String extractTarGz(String filename) {
		String decompressDir = filename.substring(filename.lastIndexOf(File.separator)+1).replace(".tar.gz", "");
		//decompressing *.tar.gz files
		File destFile = new File(downloadDirectory + File.separator + decompressDir);
		if (! destFile.exists()){
			destFile.mkdir(); 
		}
		TarArchiveEntry tarEntry = null;
		try {
			TarArchiveInputStream tis = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(filename)));
			// tarIn is a TarArchiveInputStream
			while ((tarEntry = tis.getNextTarEntry()) != null) {
				File outputFile = new File(destFile + File.separator + tarEntry.getName());

				if (tarEntry.isDirectory()) {
					if(!outputFile.exists()){
						outputFile.mkdirs();
					}
				} else {
					outputFile.getParentFile().mkdirs();
					FileOutputStream fos = new FileOutputStream(outputFile); 
					IOUtils.copy(tis, fos);
					fos.close();
				}
			}
			tis.close();

		} catch (IOException e) {
			e.printStackTrace();
			consoleWrite(e.getMessage());
		}		
		return downloadDirectory + File.separator + decompressDir;
	}

	public void createBackup(int selectedAppIndex, String environment) {
		String appid = apps.getJSONObject(AppIndexByListIndex(selectedAppIndex)).getString(APP_ID);
		if (appid != null && !appid.isEmpty() && environment != null && !environment.isEmpty()) {
			this.runnable = new Runnable() {
				public void run()
				{	
					MediaType JSON = MediaType.parse("application/json; charset=utf-8");
					RequestBody requestBody = RequestBody.create(JSON, "{\"comment\":\"Backup tool " + apiuser + "\"}");
					Request request = new Request.Builder()
							.url(MXBACKUPAPIV1+"/"+appid+"/environments/"+environment+"/snapshots/")
							.post(requestBody)
							.addHeader(MENDIX_USER_NAME, apiuser)
							.addHeader(MENDIX_API_KEY, apikey)
							.build();

					try {
						OkHttpClient client = getClient(60*60);
						Response response = client.newCall(request).execute();
						String result = response.body().string();
						if (response.isSuccessful()) {
							consoleWrite("Backup created");
							consoleWrite(result);
							// add to internal list and ui.
							JSONObject addbackup = new JSONObject(result);
							SimpleDateFormat dt1 = new SimpleDateFormat("yyyy-MM-dd HH:mm");
							// update ui
							display.asyncExec (new Runnable () {
								public void run () {
									backuplist.add(dt1.format(new Date(addbackup.getLong(CREATED_ON))),0);
								}
							});
							backups.put(addbackup);
						} else {
							consoleWrite("Error creating backup");
							consoleWrite(result);
						}
					} catch (IOException exp) {
						consoleWrite("Error getting list " + exp.getMessage());
					}
				}
			};
			downloadThread = new Thread(this.runnable);		
			downloadThread.start();
		} else {
			consoleWrite("Please select an application and environment");
		}
	}


	public void RestoreOnly(int selectedAppIndex, int selectedBackupIndex, List backuplist,  String environment, boolean doRestore) {
		String postgresPort = this.postgresPort;
		this.runnable = new Runnable() {
			public void run()
			{		
				String dbname = "";
				String filename = "";
				if (environment == null || environment.isEmpty()) {
					consoleWrite("Select a valid environment");
					return;
				}
				if (apps != null) {
					String appid = apps.getJSONObject(AppIndexByListIndex(selectedAppIndex)).getString(APP_ID);
					String backupid = backups.getJSONObject(selectedBackupIndex).getString(SNAPSHOT_ID);
					Long createdOn = 0L;
					// if a backup is being created createdon is null
					if (!backups.getJSONObject(selectedBackupIndex).isNull(CREATED_ON)) {
						createdOn = backups.getJSONObject(selectedBackupIndex).getLong(CREATED_ON);
						//createdOn = parseJSONDate(backups.getJSONObject(selectedBackupIndex).getString(CREATED_ON));
					}					
					OkHttpClient client = getClient(60);

					Request request = new Request.Builder()
							.url(MXBACKUPAPIV1+"/"+appid+"/environments/"+environment+"/snapshots/"+backupid)
							.get()
							.addHeader(MENDIX_USER_NAME, apiuser)
							.addHeader(MENDIX_API_KEY, apikey)
							.build();

					try {
						Response response = client.newCall(request).execute();
						String result = response.body().string();
						if (response.isSuccessful()) {
							String url = new JSONObject(result).getString("DatabaseOnly");	
							String saveDir = downloadDirectory;
							filename = downloadFile(url, appid, environment, saveDir, true, false); // todo store the scope (in/excluding docs) in the backup inside
						} else {
							consoleWrite("Error getting backup link");
							consoleWrite(result);
						}
					} catch (IOException exp) {
						consoleWrite("Error " + exp.getMessage());
						exp.printStackTrace();
					}
					if (filename.isEmpty()) {
						return;
					}
					dbname = appid + backupid;
					dbname = getTargetDatabaseName(appid, environment, new Date(createdOn));
				}
				if (doRestore) {
					try {
						// create the database
						//String commandcreate = "\"C:/Program Files/PostgreSQL/"+postgresversion+"/bin/psql.exe\" --username \"" + username + "\" --no-password --dbname postgres --command \"Create Database \""+dbname+"\"\"";			
						String commandcreate = "\""+postgresDirectory +"/bin/psql.exe\" --port " + postgresPort + " --username \"" + username + "\" --no-password --dbname postgres --command \"Create Database \"\""+dbname+"\"\"\"";
						int exitCode = runCommand(commandcreate, false);
						if (exitCode == 0 ) {
							consoleWrite("database created");
						} else {
							return;
						}
						// count the contents ( only for progressbar)
						restorelinecount = 0;
						String commandlist = "\""+postgresDirectory +"/bin/pg_restore.exe\" -l \"" + filename+ "\"";
						runCommand(commandlist, true);
						// restore the database 
						setStyledText("Restoring database");
						String command = "\""+postgresDirectory +"/bin/pg_restore.exe\" --host localhost --port " + postgresPort + " --jobs 2 --username \"" + username + "\" --dbname \""+dbname+"\" --no-owner --no-password  --verbose \"" + filename+ "\"";
						int exitCode2 = runCommand(command, false);
						if (exitCode2 == 0) {
							consoleWrite("Database restored as " + dbname);
						} else {
							consoleWrite("Could not restore database");
						}
						setProgress(0);
						setStyledText(dbname);
					} catch (IOException e2) {
						System.out.println(e2);
					} catch (InterruptedException e1) {
						System.out.println(e1);
					}
				}
			}
		};
		downloadThread = new Thread(this.runnable);		
		downloadThread.start(); 

	}


	/**
	 * run an executable on disk.
	 * 
	 * @param command Command including parameters
	 * @param storelinecount If true get the number of output lines and store in restorelinecount
	 * @return -1 if fails, 0 if ok
	 * @throws IOException	Exception
	 * @throws InterruptedException Exception
	 */
	public int runCommand(String command, Boolean storelinecount) throws IOException, InterruptedException {
		if (downloadThread.isInterrupted()) {
			return -1;
		}
		consoleWrite(command);
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.environment().put("PGPASSWORD", this.password);
		pb.redirectErrorStream(true);
		runProcess = pb.start();
		BufferedReader reader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
		String line;
		int linecount = 0;
		if (restorelinecount > 0) {
			setProgressMax(restorelinecount);
			setProgress(0);
		}
		while ((line = reader.readLine()) != null) {
			// storelinecount is only for counting the contents of the backup
			if (!storelinecount) {
				consoleWrite(line);
			}
			setProgress(linecount++);
		}
		if (storelinecount) {
			restorelinecount = linecount * 3; // launch/ run/ finished
		}
		return runProcess.waitFor();
	}
	/**
	 * create a dialog with ok and cancel buttons and a question icon
	 * 
	 * @param message	message to show in messagebox without program disturbing
	 * @param shell	swing shell
	 */
	public void ShowMessage(String message, Shell shell) {

		MessageBox dialog = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK| SWT.CANCEL);
		dialog.setText("Backup tool");
		dialog.setMessage(message);

		// open dialog and await user selection
		dialog.open();
	}
	/**
	 * write a message to list box on screen. Console is updated and background processes will proceed.
	 * 
	 * @param message	Message to write
	 */
	void consoleWrite(String message) {
		display.asyncExec (new Runnable () {
			public void run () {
				if (consoleList != null && message != null) {
					try {
						consoleList.add(message);
						consoleList.getParent().update();
						consoleList.setTopIndex(consoleList.getItemCount() - 1);
					} catch (Exception e) {
						System.out.print(e);
					}
				}
			}
		});
	}
	/**
	 * set the progress of the progress bar
	 * 
	 * @param progress value between zero and previously set max
	 */
	private void setProgress(int progress) {
		display.asyncExec (new Runnable () {
			public void run () {
				if (progressBar != null) {
					progressBar.setSelection(progress);
				}
			}
		});
	}
	/**
	 * Set the max of the progess bar if exist
	 * 
	 * @param max Maximum value
	 */
	private void setProgressMax(int max) {
		display.asyncExec (new Runnable () {
			public void run () {
				if (progressBar != null) {
					progressBar.setMaximum(max);
				}
			}
		});
	}
	/**
	 * Set the text bar in the bottom if exist
	 * 
	 * @param message any text
	 */
	protected void setStyledText(String message) {
		display.asyncExec (new Runnable () {
			public void run () {
				styledText.setText(message);
			}
		});

	}
	/**
	 * symmetric encrypt string
	 * 
	 * @param key encryption password
	 * @param valueToEncrypt Value you want to encrypt
	 * @return encrypted string
	 * @throws Exception exception 
	 */
	public String encryptString(String key, String valueToEncrypt) throws Exception
	{
		if (valueToEncrypt == null)
			return null;
		Cipher c = Cipher.getInstance("AES/CBC/PKCS5PADDING");
		SecretKeySpec k = new SecretKeySpec(key.getBytes(), "AES");
		c.init(Cipher.ENCRYPT_MODE, k);
		byte[] encryptedData = c.doFinal(valueToEncrypt.getBytes());
		byte[] iv = c.getIV();

		return new String(Base64.encodeBase64(iv)) + ";" + new String(Base64.encodeBase64(encryptedData));
	}
	/**
	 * decrypt string from preferences
	 * 
	 * @param key key to use for decrypting
	 * @param valueToDecrypt encrypted string
	 * @return decrypted string
	 */
	public String decryptString(String key, String valueToDecrypt) 
	{
		if (valueToDecrypt == null)
			return null;
		try {
			Cipher c = Cipher.getInstance("AES/CBC/PKCS5PADDING");
			SecretKeySpec k = new SecretKeySpec(key.getBytes(), "AES");
			String[] s = valueToDecrypt.split(";");
			if (s.length < 2) //Not an encrypted string, just return the original value.
				return valueToDecrypt;
			byte[] iv = Base64.decodeBase64(s[0].getBytes());
			byte[] encryptedData = Base64.decodeBase64(s[1].getBytes());
			c.init(Cipher.DECRYPT_MODE, k, new IvParameterSpec(iv));
			return new String(c.doFinal(encryptedData));
		} catch (Exception e) {
			consoleWrite("Could not decrypt string");
			return "";
		}
	}

	/**
	 * Downloads a file from a URL
	 * 
	 * @param fileURL HTTP URL of the file to be downloaded
	 * @param appid	Application id
	 * @param environment environment (acceptance, production, etc)
	 * @param saveDir path of the directory to save the file
	 * @param doDownload if false the file is not downloaded but only the filename is returned. For restore already downloaded file
	 * @return filename of downloaded file
	 * @throws IOException exception
	 */


	public String downloadFile(String fileURL, String appid, String environment, String saveDir, Boolean doDownload, Boolean includeDocuments)
			throws IOException {
		String result = "";
		URL url;
		try {
			url = new URL(fileURL);
			Preferences prefs = getPreferences();
			HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
			int responseCode = httpConn.getResponseCode();

			// always check HTTP response code first
			if (responseCode == HttpURLConnection.HTTP_OK) {
				String fileName = "";
				String disposition = httpConn.getHeaderField("Content-Disposition");
				//int contentLength = httpConn.getContentLength();
				String ext = includeDocuments?"-doc":"";
				Long maxCount = prefs.getLong(appid + ext, 0);
				setProgressMax(1000);

				if (disposition != null) {
					// extracts file name from header field
					int index = disposition.indexOf(FILENAMETAG);
					if (index > 0) {
						fileName = appid + "-" + shortEnv(environment) + "-" + disposition.substring(index + FILENAMETAG.length(), disposition.length());
					}
				} else {
					// extracts file name from URL
					fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1, fileURL.length());
				}

				consoleWrite("fileName = " + fileName);
				consoleWrite("Downloading");

				// opens input stream from the HTTP connection
				InputStream inputStream = httpConn.getInputStream();
				String saveFilePath = saveDir + File.separator + fileName;

				// opens an output stream to save into file
				if (doDownload) {
					FileOutputStream outputStream = new FileOutputStream(saveFilePath);
					try {
						long c = 0;
						int bytesRead = -1;
						long totalbytesRead = 0L;
						byte[] buffer = new byte[BUFFER_SIZE];
						while ((bytesRead = inputStream.read(buffer)) != -1 && !downloadThread.isInterrupted()) {
							outputStream.write(buffer, 0, bytesRead);
							totalbytesRead += bytesRead;
							// prevent the updates, too often will slow the gui
							if (c++==20) {
								c = 0;
								Long prog = Math.min(100, totalbytesRead);
								if (maxCount > 0) {
									prog = 1000*totalbytesRead/maxCount;
								}
								setProgress(prog.intValue());
								setStyledText(totalbytesRead/(1024*1024) + " MB read");
							}
						}
						// store for next time progress bar
						prefs.putLong(SIZEPREFIX+appid+ext, totalbytesRead);
					} catch (IOException e) {
						consoleWrite("Error " + e.getMessage());
						e.printStackTrace();
					} finally {
						try {
							outputStream.close();
							inputStream.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}

				if (downloadThread.isInterrupted()) {
					return result;
				}
				consoleWrite("File downloaded");
				setProgress(0);
				result = saveFilePath;
			} else {
				consoleWrite("No file to download. Server replied HTTP code: " + responseCode);
			}
			httpConn.disconnect();
		} catch (MalformedURLException e) {
			consoleWrite("Error " + e.getMessage());
			e.printStackTrace();
		} catch (FileNotFoundException e1) {
			consoleWrite("Error " + e1.getMessage());
			e1.printStackTrace();
		} catch (IOException e1) {
			consoleWrite("Error " + e1.getMessage());
			e1.printStackTrace();
		}

		return result;
	}
	/**
	 * return short name for environment
	 * 
	 * @param environment either acceptance or production or test
	 * 
	 * @return accp, prod or test
	 */

	private String shortEnv(String environment) {
		String shortEnv = "unknown";
		if (environment != null && !environment.isEmpty()) {
			//default name
			shortEnv = environment;
			// map regular names
			final Map<String, String> envToShortEnv = new HashMap<String, String> ();
			envToShortEnv.put("production", "prod");
			envToShortEnv.put("acceptance", "accp");
			envToShortEnv.put("test", "test");
			if (envToShortEnv.containsKey(environment.toLowerCase())) {
				shortEnv = envToShortEnv.get(environment.toLowerCase());
			}
		}
		return shortEnv;
	}

	/**
	 * stop all running threads etc.
	 */
	public void interrupt(){
		consoleWrite("Canceled");
		setProgress(0);
		if (runProcess != null && runProcess.isAlive()) {
			runProcess.destroy();
		}
		if (downloadThread != null && downloadThread.isAlive()) {
			downloadThread.interrupt();
		}
	}
	/**
	 * Display a wait cursor on screen.
	 * 
	 * @param shell	Swing shell
	 */
	public void setCursorWait(Shell shell){
		if(cursor != null)
			cursor.dispose();

		cursor = new Cursor(display, SWT.CURSOR_WAIT);

		shell.setCursor(cursor);
	}
	/**
	 * set the cursor normal, users knows that the process is ready
	 * @param shell	Swing shell
	 */

	public void setCursorDefault(Shell shell) {
		if(cursor != null) {
			cursor.dispose();
		}
		cursor = new Cursor(display, SWT.CURSOR_ARROW);

		shell.setCursor(cursor);
	}
	/*
	 * Open a browser
	 */

	public void OpenBrowser(String path, int selectedApp) {
		try {
			if (selectedApp > -1 && AppIndexByListIndex(selectedApp) > -1) {
				String appid = apps.getJSONObject(AppIndexByListIndex(selectedApp)).getString("ProjectId");
				if (appid != null && !appid.isEmpty()) {
					java.awt.Desktop.getDesktop().browse(new URI(path + appid ));
				}
			}
		} catch (IOException e1) {
			consoleWrite("Error " + e1.getMessage());
		} catch (URISyntaxException e1) {
			consoleWrite("Error " + e1.getMessage());
		}		
	}
	/**
	 * get the list of recently used items (names), which is created by addMRU
	 * @return list of strings with MRU items, can be empty
	 */

	public String[] getMRUItems() {
		Preferences prefs = getPreferences();

		ArrayList<String> mrulist = new ArrayList<String>();
		for (int i = 0; i<MAX_MRU; i++) {
			int appindex = prefs.getInt(MRUPREFIX+i, -1);
			if (appindex >= 0) {
				String name = apps.getJSONObject(appindex).getString("Name");
				if (!mrulist.contains(name)) {
					mrulist.add(name);
				}
			}
		}
		return mrulist.toArray(new String[mrulist.size()]);
	}

	/**
	 * Get the preferences storage
	 * @return Preferences
	 */
	private Preferences getPreferences() {
		return Preferences.userNodeForPackage(MendixUtil.class);
	}
	/**
	 * Add an app to the MRU list, max 6 items. Latest item is not put on top.
	 * @param appindex index of application
	 */

	public void addMRU(int appindex) {
		Preferences prefs = getPreferences();

		// shift them up.
		for (int i=MAX_MRU; i>=0; i--) {
			prefs.putInt(MRUPREFIX+i, prefs.getInt(MRUPREFIX+(i-1), -1));
		}
		prefs.putInt(MRUPREFIX+"0", appindex);
	}
	/**
	 * Clear the MRU list. 
	 */

	public void clearMRU() {
		Preferences prefs = getPreferences();
		for (int i = MAX_MRU; i>=0; i--) {
			prefs.putInt(MRUPREFIX+i, -1);
		}
	}

	/**
	 * Parse the XML date from JSON
	 * Returns Date
	 */

	public java.util.Date parseJSONDate(String jsonDate) {
		if (jsonDate != null && !jsonDate.isEmpty()) {
			try {
				SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
				return parser.parse(jsonDate);
			} catch (Exception e) {
				consoleWrite("Error parsing date " + jsonDate);
				return null;
			}
		} else {
			return null;
		}

	}

}