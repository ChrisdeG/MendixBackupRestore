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
import java.time.Instant;
import java.time.format.DateTimeFormatter;
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

import okhttp3.FormBody;
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
	// retries in case of 500
	private static final int MaxRetry = 10;
	private static final String MXBACKUPAPIV1 = "https://deploy.mendix.com/api/1/apps"; //$NON-NLS-1$
	private static final String MXBACKUPAPIV2 = "https://deploy.mendix.com/api/v2/apps"; //$NON-NLS-1$
	// json fields in api response
	private static final String CREATED_ON = "CreatedOn"; //$NON-NLS-1$
	private static final String SNAPSHOT_ID = "SnapshotID"; //$NON-NLS-1$
	// storage keys
	private static final String MXAPIKEY = "mxapikey"; //$NON-NLS-1$
	private static final String MENDIX_API_KEY = "Mendix-ApiKey"; //$NON-NLS-1$
	private static final String MENDIX_USER_NAME = "Mendix-UserName"; //$NON-NLS-1$
	private static final String DBPASSWORD = "password"; //$NON-NLS-1$
	private static final String DBUSERNAME = "username"; //$NON-NLS-1$
	private static final String PGDIR = "pgdir"; // postgres directory //$NON-NLS-1$
	private static final String PGPORT = "pgport"; // postgres port //$NON-NLS-1$
	private static final String DLDIR = "donwnloaddir"; //$NON-NLS-1$
	private static final String MXAPIUSER = "mxapiuser"; //$NON-NLS-1$
	private static final String BACKUPNAMING = "backupnaming"; //$NON-NLS-1$
	private static final String SIZEPREFIX = "lastdownloadsize_"; //$NON-NLS-1$
	private static final String MRUPREFIX = "mru"; //$NON-NLS-1$
	private static final String APP_ID = "AppId"; //$NON-NLS-1$
	// symmetric key for storage
	private static final String KEY = "6GHH&Jkw2#hgfaUy"; //$NON-NLS-1$
	private static final String FILENAMETAG = "filename="; //$NON-NLS-1$
	private static final int BUFFER_SIZE = 4096 * 16;
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
	private int apiversion = 2;
	private String postgresversion;
	private int restorelinecount;
	private List consoleList;
	private List backuplist;
	private ProgressBar progressBar;
	private StyledText styledText;
	private JSONArray environments;
	private JSONArray apps;
	private JSONArray backups;
	private JSONObject backupsv2;
	private ArrayList<String> applist;
	private Display display;
	private Process runProcess;
	private Thread downloadThread;
	private Runnable runnable;
	protected String filepath;
	private static Cursor cursor = null;

	/**
	 * Constructor. Create a MendixUtil object that does all the dirty work for you.
	 * The SWT objects are passed from the ui to show lists and progress.
	 * 
	 * @param display     SWING display
	 * @param dbUsername  Database user name
	 * @param dbPassword  Database password
	 * @param consoleList List on screen with console messages
	 * @param styledText  Textbox on screen
	 * @param progressBar progressbar
	 * @param applist     application list control
	 * @param backuplist  backup list control
	 */
	public MendixUtil(Display display, String dbUsername, String dbPassword, List consoleList, StyledText styledText,
			ProgressBar progressBar, List applist, List backuplist) {
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
	 * get the current version of postgres to make a path to the executables. todo:
	 * hardcoded C:\ SELECT * FROM pg_settings WHERE --name = 'data_directory' and
	 */
	private void getPostgresfolder() {
		// Computer\HKEY_LOCAL_MACHINE\SOFTWARE\PostgreSQL\Installations\postgresql-x64-10
		postgresversion = getPostgresVersion();
		if (postgresversion != null) {
			boolean tryDir = tryCustomDirectory(postgresDirectory);
			if (!tryDir) {
				tryDir = tryDirectory(System.getenv("ProgramFiles") + "/PostgreSQL/"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (!tryDir) {
				tryDir = tryDirectory(System.getenv("ProgramFiles(X86)") + "/PostgreSQL/"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (!tryDir) {
				consoleWrite("Postgres not found " + postgresversion); //$NON-NLS-1$
			}
		} else {
			consoleWrite(
					"Postgres not found, This tool only works with postgres. Please set the directory in settings."); //$NON-NLS-1$
		}
	}

	/*
	 * Check whether the directory exists
	 **/

	private boolean tryDirectory(final String directory) {
		Path path = Paths.get(directory + postgresversion);
		while ((postgresversion.endsWith(".") || Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) //$NON-NLS-1$
				&& postgresversion.length() > 0) {
			postgresversion = postgresversion.substring(0, postgresversion.length() - 1);
			path = Paths.get(directory + postgresversion);
		}
		if (postgresversion.length() > 0) {
			consoleWrite("Found postgres folder: " + directory + postgresversion); //$NON-NLS-1$
			postgresDirectory = directory + postgresversion;
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Try a directory without version
	 * 
	 * @param directory
	 * @return
	 */

	private boolean tryCustomDirectory(final String directory) {
		if (directory != null && !directory.isEmpty()) {
			Path path = Paths.get(directory);
			if (Files.isDirectory(path)) {
				consoleWrite("Found postgres folder: " + directory); //$NON-NLS-1$
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
		apiuser = prefs.get(MXAPIUSER, ""); //$NON-NLS-1$
		username = prefs.get(DBUSERNAME, "postgres"); //$NON-NLS-1$
		password = prefs.get(DBPASSWORD, "postgres"); //$NON-NLS-1$
		postgresDirectory = prefs.get(PGDIR, postgresDirectory);
		postgresPort = prefs.get(PGPORT, "5432"); //$NON-NLS-1$
		backupNaming = prefs.get(BACKUPNAMING, "_{environment}_{date}"); //$NON-NLS-1$
		String home = System.getProperty("user.home") + File.separator + "downloads"; //$NON-NLS-1$ //$NON-NLS-2$
		downloadDirectory = prefs.get(DLDIR, home);
		try {
			apikey = decryptString(KEY, prefs.get(MXAPIKEY, "")); //$NON-NLS-1$
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
			try {
				FileWriter fw = new FileWriter(FileName);
				BufferedWriter bw = new BufferedWriter(fw);
				bw.write(apps);
				bw.close();
			} catch (IOException e) {
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
			String ret = ""; //$NON-NLS-1$

			try {
				File file = new File(FileName);
				if (file.exists()) {
					InputStream inputStream = new FileInputStream(file);

					if (inputStream != null) {
						InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
						BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
						String receiveString = ""; //$NON-NLS-1$
						StringBuilder stringBuilder = new StringBuilder();

						while ((receiveString = bufferedReader.readLine()) != null) {
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
				consoleWrite(Messages.getString("MendixUtil.6") + e.getMessage()); //$NON-NLS-1$
			} catch (IOException e) {
				consoleWrite(Messages.getString("MendixUtil.7") + e.getMessage()); //$NON-NLS-1$
			}
		}
	}

	/**
	 * @return /users/NAME/roaming/appdata/Mendixtools/apps.txt
	 */
	private String localStorage() {
		String appData = System.getenv("AppData"); //$NON-NLS-1$
		if (appData != null && !appData.isEmpty()) {
			String directoryName = appData.concat("/MendixTools"); //$NON-NLS-1$
			File directory = new File(directoryName);
			if (!directory.exists()) {
				directory.mkdir();
			}
			return directoryName + "/" + "apps.txt"; //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			return null;
		}
	}

	/**
	 * set the api user and key and store in prefs
	 * 
	 * @param apiUser           Username
	 * @param apiKey            ApiKey
	 * @param userName          postgres username
	 * @param password          postgres password
	 * @param postgresDirectory postgres directory
	 * @param postgresPort      postgres port (default 5432)
	 * @param downloadDirectory
	 */
	public void setUserAndKey(String apiUser, String apiKey, String userName, String password, String postgresDirectory,
			String postgresPort, String downloadDirectory) {
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
			consoleWrite(Messages.getString("MendixUtil.8") + e.getMessage()); //$NON-NLS-1$
		}
		// after the setting has changed try postgres again.
		getPostgresfolder();
	}

	/**
	 * Store the setting
	 * 
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
			Class.forName("org.postgresql.Driver"); //$NON-NLS-1$
			Connection connection = DriverManager.getConnection(
					"jdbc:postgresql://127.0.0.1:" + this.postgresPort + "/postgres", //$NON-NLS-1$ //$NON-NLS-2$
					username, password);
			try {
				DatabaseMetaData metadata = connection.getMetaData();
				return metadata.getDatabaseProductVersion();
			} finally {
				connection.close();
			}
		} catch (Exception e) {
			consoleWrite("Error connecting with postgres " + e.getMessage()); //$NON-NLS-1$
		}
		return result;
	}

	/**
	 * Create a database name according to naming convention if database exists a
	 * suffix _1, _2 is added. All databasenames are unique
	 * 
	 * @param appid       application id
	 * @param environment environment like production, acceptance
	 * @param createdOn   Date of the backup
	 * @return Unique databasename
	 */
	public String getTargetDatabaseName(String appid, String environment, Date createdOn) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd"); //$NON-NLS-1$
		String strDate = formatter.format(createdOn);
		String dbName = appid + backupNaming.replace("{environment}", environment).replace("{date}", strDate); //$NON-NLS-1$ //$NON-NLS-2$
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
			Class.forName("org.postgresql.Driver"); //$NON-NLS-1$
			Connection connection = DriverManager.getConnection(
					"jdbc:postgresql://127.0.0.1:" + this.postgresPort + "/postgres", //$NON-NLS-1$ //$NON-NLS-2$
					username, password);
			try {
				// SELECT datname FROM pg_catalog.pg_database WHERE lower(datname) =
				// 'project_21'
				Statement statement = connection.createStatement();
				while (suffixnumber < 1000) {
					String suffix = (suffixnumber > 0) ? "_" + suffixnumber : ""; //$NON-NLS-1$ //$NON-NLS-2$
					ResultSet set = statement
							.executeQuery("SELECT count(datname) FROM pg_catalog.pg_database WHERE lower(datname) = '" //$NON-NLS-1$
									+ databasename.toLowerCase() + suffix + "'"); //$NON-NLS-1$
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
			consoleWrite(Messages.getString("MendixUtil.9") + e.getMessage()); //$NON-NLS-1$
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
		return new OkHttpClient.Builder().readTimeout(finalTimeOut, TimeUnit.SECONDS).build();
	}

	/**
	 * Call the Mendix Api and get a list of app you have access to
	 * 
	 * @param list UI control that contains the list
	 */
	public void GetAppList(List list, String filter) {

		OkHttpClient client = getClient(60);

		Request request = new Request.Builder().url(MXBACKUPAPIV1).get().addHeader(MENDIX_USER_NAME, apiuser)
				.addHeader(MENDIX_API_KEY, apikey).build();

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
					consoleWrite(Messages.getString("MendixUtil.UserOrKeyInvalid")); //$NON-NLS-1$
				} else {
					consoleWrite(Messages.getString("MendixUtil.11") + response.code()); //$NON-NLS-1$
				}
			}
		} catch (Exception e) {
			backuplist.removeAll();
			consoleWrite(Messages.getString("MendixUtil.12") + e.getMessage()); //$NON-NLS-1$
		}

	}

	public void FilterAppList(List applist2, String filter) {
		GetAppList(applist2, filter);
	}

	public void getEnvironmentList(int selectionCount, List environmentlist) {
		if (selectionCount >= 0) {
			OkHttpClient client = getClient(60);

			String appid = apps.getJSONObject(AppIndexByListIndex(selectionCount)).getString(APP_ID);
			// String projectid =
			// apps.getJSONObject(AppIndexByListIndex(selectionCount)).getString("ProjectId");

			Request request = new Request.Builder().url(MXBACKUPAPIV1 + "/" + appid + "/environments") //$NON-NLS-1$ //$NON-NLS-2$
					.get().addHeader(MENDIX_USER_NAME, apiuser).addHeader(MENDIX_API_KEY, apikey).build();

			try {
				Response response = client.newCall(request).execute();
				if (response.code() == 200) {

					String appsJSON = response.body().string();
					environments = new JSONArray(appsJSON);
					environmentlist.removeAll();
					ArrayList<String> sorted = new ArrayList<String>();
					for (int i = 0; i < environments.length(); i++) {
						String mode = environments.getJSONObject(i).getString("Mode"); //$NON-NLS-1$
						if (mode.toLowerCase().contentEquals("sandbox")) { //$NON-NLS-1$
							consoleWrite(appid + Messages.getString("MendixUtil.Sandbox")); //$NON-NLS-1$
						} else {
							sorted.add(mode);
						}
					}
					Collections.sort(sorted);
					environmentlist.setItems(sorted.toArray(new String[0]));
				}
			} catch (IOException e) {
				consoleWrite("Error getting environments" + e.getMessage()); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Parse the json from the applist and do a primitive sort
	 * 
	 * @param list   User interface List
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
				if (filter != null && !filter.isEmpty()
						&& !apps.getJSONObject(i).getString("Name").toLowerCase().contains(filter.toLowerCase())) { //$NON-NLS-1$
					skip = true;
				}
				// skip sandbox
				if (apps.getJSONObject(i).has("Url")) { //$NON-NLS-1$
					if (apps.getJSONObject(i).getString("Url").contains("mxapps.io")) { //$NON-NLS-1$ //$NON-NLS-2$
						skip = true;
					}
				} else {
					// skip mobile projects
					skip = true;
				}
				if (!skip) {
					applist.add(apps.getJSONObject(i).getString("Name")); //$NON-NLS-1$
				}
			}
			Collections.sort(applist);
			list.setItems(applist.toArray(new String[0]));
		}
	}

	/**
	 * Get the application id based on application name
	 * 
	 * @param appName Application name
	 * @return either application or empty string;
	 */
	public String AppIdByAppName(String appName) {
		for (int i = 0; i < apps.length(); i++) {
			if (apps.getJSONObject(i).getString("Name").contentEquals(appName)) { //$NON-NLS-1$
				return (apps.getJSONObject(i).getString(APP_ID));
			}
		}
		return ""; //$NON-NLS-1$
	}

	/**
	 * the applist is sorted, get the id of the original json app list. listindex is
	 * the index from the applist on screen.
	 * 
	 * @param listindex
	 * @return
	 */
	private int AppIndexByListIndex(int listindex) {
		if (listindex >= 0 && listindex < applist.size()) {
			String appName = applist.get(listindex);
			for (int i = 0; i < apps.length(); i++) {
				if (apps.getJSONObject(i).has("Name") && apps.getJSONObject(i).getString("Name").contentEquals(appName)) { //$NON-NLS-1$
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
			if (apps.getJSONObject(i).getString("Name").contentEquals(appName)) { //$NON-NLS-1$
				return (i);
			}
		}
		return -1;
	}

	/**
	 * Call mendix to retrieve a list of available backups
	 * 
	 * @param selectionCount selected line of list
	 * @param environment    selected environment (production, acceptance, test)
	 *                       return true is list has at least one item.
	 */
	public boolean GetBackupList(int selectionCount, String environment) {
		if (selectionCount >= 0) {
			try {
				int appIndex = AppIndexByListIndex(selectionCount);
				if (appIndex >= 0 && appIndex < apps.length() && apps.getJSONObject(appIndex).has(APP_ID)) {
					String appid = apps.getJSONObject(appIndex).getString(APP_ID);
					addMRU(appIndex);
					OkHttpClient client = getClient(60);
					String environmentId = findEnvironmentId(environment);

					String listUrl = MXBACKUPAPIV1 + "/" + appid + "/environments/" + environment + "/snapshots";
					if (apiversion == 2) {
						String projectid = apps.getJSONObject(appIndex).getString("ProjectId");
						listUrl = MXBACKUPAPIV2 + "/" + projectid + "/environments/" + environmentId + "/snapshots";
					}
					Request request = new Request.Builder().url(listUrl) // $NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							.get().addHeader(MENDIX_USER_NAME, apiuser).addHeader(MENDIX_API_KEY, apikey).build();
					Response response = client.newCall(request).execute();
					String result = response.body().string();
					backuplist.removeAll();
					if (response.isSuccessful()) {
						consoleWrite(result);
						SimpleDateFormat dt1 = new SimpleDateFormat("yyyy-MM-dd HH:mm"); //$NON-NLS-1$
						// SimpleDateFormat parser = new
						// SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
						if (apiversion == 2) {
							this.backupsv2 = new JSONObject(result);
							int total = backupsv2.getInt("total");
							if (backupsv2.has("snapshots")) {
								JSONArray snapshots = backupsv2.getJSONArray("snapshots");
								for (int i = 0; i < snapshots.length(); i++) {
									// if a backup is being created created on is null
									if (snapshots.optJSONObject(i) != null) {
										final String sDate = snapshots.optJSONObject(i).optString("created_at", null);
										if (sDate != null) {
											// https://stackoverflow.com/questions/2201925/converting-iso-8601-compliant-string-to-java-util-date/60214805#60214805
											// JAVA 8
											Date date = Date
													.from(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(sDate)));
											backuplist.add(dt1.format(date));
											// backuplist.add(dt1.format(parser.parse(backups.getJSONObject(i).getString(CREATED_ON))));
										}
									}
								}
							} else {
								consoleWrite("no snapshots found in JSON");
							}
							return total > 0;
						} else {
							this.backups = new JSONArray(result);
							for (int i = 0; i < backups.length(); i++) {
								// if a backup is being created created on is null
								if (!backups.getJSONObject(i).isNull(CREATED_ON)) {
									backuplist.add(dt1.format(new Date(backups.getJSONObject(i).getLong(CREATED_ON))));
									// backuplist.add(dt1.format(parser.parse(backups.getJSONObject(i).getString(CREATED_ON))));
								}
							}
							return backups.length() > 0;
						}
					} else {
						consoleWrite(Messages.getString("MendixUtil.2")); //$NON-NLS-1$
						consoleWrite(result);
						return false;
					}
				} else {
					return false;
				}
			} catch (Exception e) {
				consoleWrite(Messages.getString("MendixUtil.1") + ": " + e.getMessage() + e.getStackTrace().toString()); //$NON-NLS-1$
			}
		} else {
			return false;
		}
		return false;
	}

	public void GetBackupLinkDownloadAndRestore(final int selectedAppIndex, final int selectedBackupIndex,
			List backuplist, final String environment, final boolean doDownload, boolean doRestore,
			final boolean includeDocuments) {
		if (apiversion == 2) {
			String environmentId = findEnvironmentId(environment);
			if (environmentId != null) {
				GetBackupLinkDownloadAndRestoreV2(selectedAppIndex, selectedBackupIndex, backuplist, environment,
						environmentId, doDownload, doRestore, includeDocuments);
			} else {
				consoleWrite("Can not find environment id");
			}
		} else {
			GetBackupLinkDownloadAndRestoreV1(selectedAppIndex, selectedBackupIndex, backuplist, environment,
					doDownload, doRestore, includeDocuments);
		}
	}
	/*
	 * Find the environmentId on mode (Acceptance or Production)
	 */

	private String findEnvironmentId(String environment) {
		for (int i = 0; i < environments.length(); i++) {
			JSONObject env = environments.getJSONObject(i);
			if (env.getString("Mode").contentEquals(environment)) {
				return (env.getString("EnvironmentId"));
			}
		}
		return null;
	}

	/**
	 * Retrieve the backup link of the OnlyDatabase from the json and download the
	 * file into downloads
	 * 
	 * @param selectedAppIndex    Selected App
	 * @param selectedBackupIndex Selected backup
	 * @param backuplist          List of backups
	 * @param environment         environment like production, acceptance
	 * @param doDownload          if true downloads the file
	 * @param doRestore           if true restores the file
	 * @param includeDocuments
	 */
	public void GetBackupLinkDownloadAndRestoreV1(final int selectedAppIndex, final int selectedBackupIndex,
			List backuplist, final String environment, final boolean doDownload, boolean doRestore,
			final boolean includeDocuments) {
		String postgresPort = this.postgresPort;
		this.runnable = new Runnable() {
			public void run() {
				String dbname = ""; //$NON-NLS-1$
				String filename = ""; //$NON-NLS-1$
				if (environment == null || environment.isEmpty()) {
					consoleWrite(Messages.getString("MendixUtil.SelectValid")); //$NON-NLS-1$
					return;
				}
				if (apps != null) {
					String appid = ""; //$NON-NLS-1$
					String backupid = ""; //$NON-NLS-1$
					// Date createdOn = null;
					Long createdOn = 0L;
					try {
						appid = apps.getJSONObject(AppIndexByListIndex(selectedAppIndex)).getString(APP_ID);
						backupid = backups.getJSONObject(selectedBackupIndex).getString(SNAPSHOT_ID);
						// if a backup is being created createdon is null
						if (!backups.getJSONObject(selectedBackupIndex).isNull(CREATED_ON)) {
							// createdOn =
							// parseJSONDate(backups.getJSONObject(selectedBackupIndex).getString(CREATED_ON));
							createdOn = backups.getJSONObject(selectedBackupIndex).getLong(CREATED_ON);

						}
					} catch (Exception exp) {
						consoleWrite("Error getting list " + exp.getMessage()); //$NON-NLS-1$
					}
					OkHttpClient client = getClient(60);

					final String backupUrl = MXBACKUPAPIV1 + "/" + appid + "/environments/" + environment
							+ "/snapshots/" + backupid;
					Request request = new Request.Builder().url(backupUrl) // $NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							.get().addHeader(MENDIX_USER_NAME, apiuser).addHeader(MENDIX_API_KEY, apikey).build();

					try {
						Response response = client.newCall(request).execute();
						String result = response.body().string();
						if (response.isSuccessful()) {
							String scope = includeDocuments ? "DatabaseAndFiles" : "DatabaseOnly"; //$NON-NLS-1$ //$NON-NLS-2$
							String url = new JSONObject(result).getString(scope);
							String saveDir = downloadDirectory;
							filename = downloadFile(url, appid, environment, saveDir, doDownload, includeDocuments);
						} else {
							consoleWrite("Error getting backup link"); //$NON-NLS-1$
							consoleWrite(result);
						}
					} catch (IOException exp) {
						consoleWrite("Error getting list " + exp.getMessage()); //$NON-NLS-1$
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
							dbFilename = extractDirectory + File.separator + "db" + File.separator + "db.backup"; //$NON-NLS-1$ //$NON-NLS-2$
						}
						// create the database
						// String commandcreate = "\"C:/Program
						// Files/PostgreSQL/"+postgresversion+"/bin/psql.exe\" --username \"" + username
						// + "\" --no-password --dbname postgres --command \"Create Database
						// \""+dbname+"\"\"";
						String commandcreate = "\"" + postgresDirectory + "/bin/psql.exe\" --port " + postgresPort //$NON-NLS-1$ //$NON-NLS-2$
								+ " --username \"" + username //$NON-NLS-1$
								+ "\" --no-password --dbname postgres --command \"Create Database \"\"" + dbname //$NON-NLS-1$
								+ "\"\"\""; //$NON-NLS-1$
						int exitCode = runCommand(commandcreate, false);
						if (exitCode == 0) {
							consoleWrite("database created"); //$NON-NLS-1$
						} else {
							return;
						}
						// count the contents ( only for progressbar)
						restorelinecount = 0;
						String commandlist = "\"" + postgresDirectory + "/bin/pg_restore.exe\" -l \"" + dbFilename //$NON-NLS-1$ //$NON-NLS-2$
								+ "\""; //$NON-NLS-1$
						runCommand(commandlist, true);
						// restore the database
						setStyledText("Restoring database"); //$NON-NLS-1$
						String command = "\"" + postgresDirectory + "/bin/pg_restore.exe\" --host localhost --port " //$NON-NLS-1$ //$NON-NLS-2$
								+ postgresPort + " --jobs 2 --username \"" + username + "\" --dbname \"" + dbname //$NON-NLS-1$ //$NON-NLS-2$
								+ "\" --no-owner --no-password  --verbose \"" + dbFilename + "\""; //$NON-NLS-1$ //$NON-NLS-2$
						int exitCode2 = runCommand(command, false);
						if (exitCode2 == 0) {
							consoleWrite("Database restored as " + dbname); //$NON-NLS-1$
							if (includeDocuments) {
								consoleWrite(Messages.getString("MendixUtil.setPath")); //$NON-NLS-1$
								consoleWrite(extractDirectory + File.separator + "tree"); //$NON-NLS-1$
								consoleWrite("https://docs.mendix.com/refguide/custom-settings"); //$NON-NLS-1$
								consoleWrite(" Environments > Environment Details > Runtime > Custom Runtime Settings"); //$NON-NLS-1$
							}
						} else {
							consoleWrite("Restore returned errorcode but the database may as well be restored."); //$NON-NLS-1$
							consoleWrite("This may happen when a postgres 9 database is restored to 10 or later "); //$NON-NLS-1$
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
	/*
	 * version of void using deploy/backup api v2
	 */

	public void GetBackupLinkDownloadAndRestoreV2(final int selectedAppIndex, final int selectedBackupIndex,
			List backuplist, String environment, final String environmentId, final boolean doDownload,
			boolean doRestore, final boolean includeDocuments) {
		String postgresPort = this.postgresPort;
		this.runnable = new Runnable() {
			public void run() {
				String dbname = ""; //$NON-NLS-1$
				String filename = ""; //$NON-NLS-1$
				if (environmentId == null || environmentId.isEmpty()) {
					consoleWrite(Messages.getString("MendixUtil.SelectValid")); //$NON-NLS-1$
					return;
				}
				if (apps != null) {
					String projectId = ""; //$NON-NLS-1$
					String snapshotId = ""; //$NON-NLS-1$
					String appId = apps.getJSONObject(AppIndexByListIndex(selectedAppIndex)).getString(APP_ID);
					// Date createdOn = null;
					Date createdOn = null;
					try {
						projectId = apps.getJSONObject(AppIndexByListIndex(selectedAppIndex)).getString("ProjectId");
						snapshotId = backupsv2.getJSONArray("snapshots").getJSONObject(selectedBackupIndex)
								.getString("snapshot_id");
						// if a backup is being created createdon is null
						if (!backupsv2.getJSONArray("snapshots").getJSONObject(selectedBackupIndex)
								.isNull("created_at")) {
							// createdOn =
							// parseJSONDate(backups.getJSONObject(selectedBackupIndex).getString(CREATED_ON));
							String sDate = backupsv2.getJSONArray("snapshots").getJSONObject(selectedBackupIndex)
									.getString("created_at");
							createdOn = Date.from(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(sDate)));

						}
					} catch (Exception exp) {
						consoleWrite("Error getting list " + exp.getMessage()); //$NON-NLS-1$
					}
					OkHttpClient client = getClient(60);

					RequestBody formBody = new FormBody.Builder().build();
					// request an archive
					String scope = includeDocuments ? "files_and_database" : "database_only"; //$NON-NLS-1$ //$NON-NLS-2$
					final String backupUrl = MXBACKUPAPIV2 + "/" + projectId + "/environments/" + environmentId
							+ "/snapshots/" + snapshotId + "/archives?data_type=" + scope;
					Request request = new Request.Builder().url(backupUrl) // $NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							.post(formBody).addHeader(MENDIX_USER_NAME, apiuser).addHeader(MENDIX_API_KEY, apikey)
							.build();
					String result = "";
					try {
						Response response = client.newCall(request).execute();
						result = response.body().string();
						if (response.code() == 200) {
							// String scope = includeDocuments ? "DatabaseAndFiles" : "DatabaseOnly";
							// //$NON-NLS-1$ //$NON-NLS-2$
							// String url = new JSONObject(result).getString(scope);
							String archiveId = new JSONObject(result).getString("archive_id");
							String saveDir = downloadDirectory;
							filename = downloadFileV2(appId, projectId, environment, environmentId, snapshotId,
									archiveId, saveDir, doDownload, includeDocuments);
						} else {
							consoleWrite("Error getting backup link"); //$NON-NLS-1$
							consoleWrite(result);
						}
					} catch (IOException exp) {
						consoleWrite("Error getting list " + exp.getMessage()); //$NON-NLS-1$
						consoleWrite(result);
					}
					if (filename.isEmpty()) {
						return;
					}
					dbname = appId + snapshotId;
					dbname = getTargetDatabaseName(appId, environment, createdOn);
				}
				if (doRestore) {
					try {
						String dbFilename = filename;
						String extractDirectory = null;
						if (includeDocuments) {
							// todo get extract dir - move file to target directory
							extractDirectory = extractTarGz(filename);
							dbFilename = extractDirectory + File.separator + "db" + File.separator + "db.backup"; //$NON-NLS-1$ //$NON-NLS-2$
						}
						// create the database
						// String commandcreate = "\"C:/Program
						// Files/PostgreSQL/"+postgresversion+"/bin/psql.exe\" --username \"" + username
						// + "\" --no-password --dbname postgres --command \"Create Database
						// \""+dbname+"\"\"";
						String commandcreate = "\"" + postgresDirectory + "/bin/psql.exe\" --port " + postgresPort //$NON-NLS-1$ //$NON-NLS-2$
								+ " --username \"" + username //$NON-NLS-1$
								+ "\" --no-password --dbname postgres --command \"Create Database \"\"" + dbname //$NON-NLS-1$
								+ "\"\"\""; //$NON-NLS-1$
						int exitCode = runCommand(commandcreate, false);
						if (exitCode == 0) {
							consoleWrite("database created"); //$NON-NLS-1$
						} else {
							return;
						}
						// count the contents ( only for progressbar)
						restorelinecount = 0;
						String commandlist = "\"" + postgresDirectory + "/bin/pg_restore.exe\" -l \"" + dbFilename //$NON-NLS-1$ //$NON-NLS-2$
								+ "\""; //$NON-NLS-1$
						runCommand(commandlist, true);
						// restore the database
						setStyledText("Restoring database"); //$NON-NLS-1$
						String command = "\"" + postgresDirectory + "/bin/pg_restore.exe\" --host localhost --port " //$NON-NLS-1$ //$NON-NLS-2$
								+ postgresPort + " --jobs 2 --username \"" + username + "\" --dbname \"" + dbname //$NON-NLS-1$ //$NON-NLS-2$
								+ "\" --no-owner --no-password  --verbose \"" + dbFilename + "\""; //$NON-NLS-1$ //$NON-NLS-2$
						int exitCode2 = runCommand(command, false);
						if (exitCode2 == 0) {
							consoleWrite("Database restored as " + dbname); //$NON-NLS-1$
							if (includeDocuments) {
								consoleWrite(Messages.getString("MendixUtil.setPath")); //$NON-NLS-1$
								consoleWrite(extractDirectory + File.separator + "tree"); //$NON-NLS-1$
								consoleWrite("https://docs.mendix.com/refguide/custom-settings"); //$NON-NLS-1$
								consoleWrite(" Environments > Environment Details > Runtime > Custom Runtime Settings"); //$NON-NLS-1$
							}
						} else {
							consoleWrite("Restore returned errorcode but the database may as well be restored."); //$NON-NLS-1$
							consoleWrite("This may happen when a postgres 9 database is restored to 10 or later "); //$NON-NLS-1$
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
		String decompressDir = filename.substring(filename.lastIndexOf(File.separator) + 1).replace(".tar.gz", ""); //$NON-NLS-1$ //$NON-NLS-2$
		// decompressing *.tar.gz files
		File destFile = new File(downloadDirectory + File.separator + decompressDir);
		if (!destFile.exists()) {
			destFile.mkdir();
		}
		TarArchiveEntry tarEntry = null;
		try {
			TarArchiveInputStream tis = new TarArchiveInputStream(
					new GzipCompressorInputStream(new FileInputStream(filename)));
			// tarIn is a TarArchiveInputStream
			while ((tarEntry = tis.getNextTarEntry()) != null) {
				File outputFile = new File(destFile + File.separator + tarEntry.getName());

				if (tarEntry.isDirectory()) {
					if (!outputFile.exists()) {
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
				public void run() {
					MediaType JSON = MediaType.parse("application/json; charset=utf-8"); //$NON-NLS-1$
					RequestBody requestBody = RequestBody.create(JSON, "{\"comment\":\"Backup tool " + apiuser + "\"}"); //$NON-NLS-1$ //$NON-NLS-2$
					Request request = new Request.Builder()
							.url(MXBACKUPAPIV1 + "/" + appid + "/environments/" + environment + "/snapshots/") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							.post(requestBody).addHeader(MENDIX_USER_NAME, apiuser).addHeader(MENDIX_API_KEY, apikey)
							.build();

					try {
						OkHttpClient client = getClient(60 * 60);
						Response response = client.newCall(request).execute();
						String result = response.body().string();
						if (response.isSuccessful()) {
							consoleWrite(Messages.getString("MendixUtil.5")); //$NON-NLS-1$
							consoleWrite(result);
							// add to internal list and ui.
							JSONObject addbackup = new JSONObject(result);
							SimpleDateFormat dt1 = new SimpleDateFormat("yyyy-MM-dd HH:mm"); //$NON-NLS-1$
							// update ui
							display.asyncExec(new Runnable() {
								public void run() {
									backuplist.add(dt1.format(new Date(addbackup.getLong(CREATED_ON))), 0);
								}
							});
							backups.put(addbackup);
						} else {
							consoleWrite(Messages.getString("MendixUtil.4")); //$NON-NLS-1$
							consoleWrite(result);
						}
					} catch (IOException exp) {
						consoleWrite(Messages.getString("MendixUtil.3") + ": " + exp.getMessage()); //$NON-NLS-1$
					}
				}
			};
			downloadThread = new Thread(this.runnable);
			downloadThread.start();
		} else {
			consoleWrite("Please select an application and environment"); //$NON-NLS-1$
		}
	}

	public void RestoreOnly(int selectedAppIndex, int selectedBackupIndex, List backuplist, String environment,
			boolean doRestore) {
		String postgresPort = this.postgresPort;
		this.runnable = new Runnable() {
			public void run() {
				String dbname = ""; //$NON-NLS-1$
				String filename = ""; //$NON-NLS-1$
				if (environment == null || environment.isEmpty()) {
					consoleWrite("Select a valid environment"); //$NON-NLS-1$
					return;
				}
				if (apps != null) {
					String appid = apps.getJSONObject(AppIndexByListIndex(selectedAppIndex)).getString(APP_ID);
					String backupid = backups.getJSONObject(selectedBackupIndex).getString(SNAPSHOT_ID);
					Long createdOn = 0L;
					// if a backup is being created createdon is null
					if (!backups.getJSONObject(selectedBackupIndex).isNull(CREATED_ON)) {
						createdOn = backups.getJSONObject(selectedBackupIndex).getLong(CREATED_ON);
						// createdOn =
						// parseJSONDate(backups.getJSONObject(selectedBackupIndex).getString(CREATED_ON));
					}
					OkHttpClient client = getClient(60);

					Request request = new Request.Builder()
							.url(MXBACKUPAPIV1 + "/" + appid + "/environments/" + environment + "/snapshots/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
									+ backupid)
							.get().addHeader(MENDIX_USER_NAME, apiuser).addHeader(MENDIX_API_KEY, apikey).build();

					try {
						Response response = client.newCall(request).execute();
						String result = response.body().string();
						if (response.isSuccessful()) {
							String url = new JSONObject(result).getString("DatabaseOnly"); //$NON-NLS-1$
							String saveDir = downloadDirectory;
							filename = downloadFile(url, appid, environment, saveDir, true, false); // todo store the
																									// scope
																									// (in/excluding
																									// docs) in the
																									// backup inside
						} else {
							consoleWrite("Error getting backup link"); //$NON-NLS-1$
							consoleWrite(result);
						}
					} catch (IOException exp) {
						consoleWrite("Error " + exp.getMessage()); //$NON-NLS-1$
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
						// String commandcreate = "\"C:/Program
						// Files/PostgreSQL/"+postgresversion+"/bin/psql.exe\" --username \"" + username
						// + "\" --no-password --dbname postgres --command \"Create Database
						// \""+dbname+"\"\"";
						String commandcreate = "\"" + postgresDirectory + "/bin/psql.exe\" --port " + postgresPort //$NON-NLS-1$ //$NON-NLS-2$
								+ " --username \"" + username //$NON-NLS-1$
								+ "\" --no-password --dbname postgres --command \"Create Database \"\"" + dbname //$NON-NLS-1$
								+ "\"\"\""; //$NON-NLS-1$
						int exitCode = runCommand(commandcreate, false);
						if (exitCode == 0) {
							consoleWrite("database created"); //$NON-NLS-1$
						} else {
							return;
						}
						// count the contents ( only for progressbar)
						restorelinecount = 0;
						String commandlist = "\"" + postgresDirectory + "/bin/pg_restore.exe\" -l \"" + filename + "\""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						runCommand(commandlist, true);
						// restore the database
						setStyledText("Restoring database"); //$NON-NLS-1$
						String command = "\"" + postgresDirectory + "/bin/pg_restore.exe\" --host localhost --port " //$NON-NLS-1$ //$NON-NLS-2$
								+ postgresPort + " --jobs 2 --username \"" + username + "\" --dbname \"" + dbname //$NON-NLS-1$ //$NON-NLS-2$
								+ "\" --no-owner --no-password  --verbose \"" + filename + "\""; //$NON-NLS-1$ //$NON-NLS-2$
						int exitCode2 = runCommand(command, false);
						if (exitCode2 == 0) {
							consoleWrite("Database restored as " + dbname); //$NON-NLS-1$
						} else {
							consoleWrite("Could not restore database"); //$NON-NLS-1$
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
	 * @param command        Command including parameters
	 * @param storelinecount If true get the number of output lines and store in
	 *                       restorelinecount
	 * @return -1 if fails, 0 if ok
	 * @throws IOException          Exception
	 * @throws InterruptedException Exception
	 */
	public int runCommand(String command, Boolean storelinecount) throws IOException, InterruptedException {
		if (downloadThread.isInterrupted()) {
			return -1;
		}
		consoleWrite(command);
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.environment().put("PGPASSWORD", this.password); //$NON-NLS-1$
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
	 * @param message message to show in messagebox without program disturbing
	 * @param shell   swing shell
	 */
	public void ShowMessage(String message, Shell shell) {

		MessageBox dialog = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK | SWT.CANCEL);
		dialog.setText("Backup tool"); //$NON-NLS-1$
		dialog.setMessage(message);

		// open dialog and await user selection
		dialog.open();
	}

	/**
	 * write a message to list box on screen. Console is updated and background
	 * processes will proceed.
	 * 
	 * @param message Message to write
	 */
	void consoleWrite(String message) {
		display.asyncExec(new Runnable() {
			public void run() {
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
		display.asyncExec(new Runnable() {
			public void run() {
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
		display.asyncExec(new Runnable() {
			public void run() {
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
		display.asyncExec(new Runnable() {
			public void run() {
				styledText.setText(message);
			}
		});

	}

	/**
	 * symmetric encrypt string
	 * 
	 * @param key            encryption password
	 * @param valueToEncrypt Value you want to encrypt
	 * @return encrypted string
	 * @throws Exception exception
	 */
	public String encryptString(String key, String valueToEncrypt) throws Exception {
		if (valueToEncrypt == null)
			return null;
		Cipher c = Cipher.getInstance("AES/CBC/PKCS5PADDING"); //$NON-NLS-1$
		SecretKeySpec k = new SecretKeySpec(key.getBytes(), "AES"); //$NON-NLS-1$
		c.init(Cipher.ENCRYPT_MODE, k);
		byte[] encryptedData = c.doFinal(valueToEncrypt.getBytes());
		byte[] iv = c.getIV();

		return new String(Base64.encodeBase64(iv)) + ";" + new String(Base64.encodeBase64(encryptedData)); //$NON-NLS-1$
	}

	/**
	 * decrypt string from preferences
	 * 
	 * @param key            key to use for decrypting
	 * @param valueToDecrypt encrypted string
	 * @return decrypted string
	 */
	public String decryptString(String key, String valueToDecrypt) {
		if (valueToDecrypt == null)
			return null;
		try {
			Cipher c = Cipher.getInstance("AES/CBC/PKCS5PADDING"); //$NON-NLS-1$
			SecretKeySpec k = new SecretKeySpec(key.getBytes(), "AES"); //$NON-NLS-1$
			String[] s = valueToDecrypt.split(";"); //$NON-NLS-1$
			if (s.length < 2) // Not an encrypted string, just return the original value.
				return valueToDecrypt;
			byte[] iv = Base64.decodeBase64(s[0].getBytes());
			byte[] encryptedData = Base64.decodeBase64(s[1].getBytes());
			c.init(Cipher.DECRYPT_MODE, k, new IvParameterSpec(iv));
			return new String(c.doFinal(encryptedData));
		} catch (Exception e) {
			consoleWrite("Could not decrypt string"); //$NON-NLS-1$
			return ""; //$NON-NLS-1$
		}
	}

	/**
	 * Downloads a file from a URL
	 * 
	 * @param fileURL     HTTP URL of the file to be downloaded
	 * @param appid       Application id
	 * @param environment environment (acceptance, production, etc)
	 * @param saveDir     path of the directory to save the file
	 * @param doDownload  if false the file is not downloaded but only the filename
	 *                    is returned. For restore already downloaded file
	 * @return filename of downloaded file
	 * @throws IOException exception
	 */

	public String downloadFile(String fileURL, String appid, String environment, String saveDir, Boolean doDownload,
			Boolean includeDocuments) throws IOException {
		String result = ""; //$NON-NLS-1$
		URL url;
		try {
			url = new URL(fileURL);
			Preferences prefs = getPreferences();
			HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
			int responseCode = httpConn.getResponseCode();
			int retrycount = 0;
			while (responseCode == 500 && retrycount++ < MaxRetry) {
				consoleWrite("Backup is not ready in Mendix cloud, will retry in 30 seconds, please wait");
				TimeUnit.SECONDS.sleep(30);
				httpConn = (HttpURLConnection) url.openConnection();
				responseCode = httpConn.getResponseCode();
			}
			if (responseCode == 500 && retrycount >= 10) {
				consoleWrite("Backup retry failed");
			}

			// always check HTTP response code first
			if (responseCode == HttpURLConnection.HTTP_OK) {
				String fileName = ""; //$NON-NLS-1$
				String disposition = httpConn.getHeaderField("Content-Disposition"); //$NON-NLS-1$
				// int contentLength = httpConn.getContentLength();
				String ext = includeDocuments ? "-doc" : ""; //$NON-NLS-1$ //$NON-NLS-2$
				Long maxCount = prefs.getLong(SIZEPREFIX + appid + ext, 0);
				setProgressMax(1000);

				if (disposition != null) {
					// extracts file name from header field
					int index = disposition.indexOf(FILENAMETAG);
					if (index > 0) {
						fileName = appid + "-" + shortEnv(environment) + "-" //$NON-NLS-1$ //$NON-NLS-2$
								+ disposition.substring(index + FILENAMETAG.length(), disposition.length());
					}
				} else {
					// extracts file name from URL
					fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1, fileURL.length()); //$NON-NLS-1$
				}

				consoleWrite("fileName = " + fileName); //$NON-NLS-1$
				consoleWrite("Downloading"); //$NON-NLS-1$

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
							if (c++ == 20) {
								c = 0;
								Long prog = Math.min(100, totalbytesRead);
								if (maxCount > 0) {
									prog = 1000 * totalbytesRead / maxCount;
								}
								setProgress(prog.intValue());
								setStyledText(totalbytesRead / (1024 * 1024) + " MB read"); //$NON-NLS-1$
							}
						}
						// store for next time progress bar
						prefs.putLong(SIZEPREFIX + appid + ext, totalbytesRead);
					} catch (IOException e) {
						consoleWrite("Error " + e.getMessage()); //$NON-NLS-1$
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
				consoleWrite("File downloaded"); //$NON-NLS-1$
				setProgress(0);
				result = saveFilePath;
			} else {
				consoleWrite("No file to download. Server replied HTTP code: " + responseCode); //$NON-NLS-1$
			}
			httpConn.disconnect();
		} catch (MalformedURLException e) {
			consoleWrite("Error " + e.getMessage()); //$NON-NLS-1$
			e.printStackTrace();
		} catch (FileNotFoundException e1) {
			consoleWrite("Error " + e1.getMessage()); //$NON-NLS-1$
			e1.printStackTrace();
		} catch (IOException e1) {
			consoleWrite("Error " + e1.getMessage()); //$NON-NLS-1$
			e1.printStackTrace();
		} catch (InterruptedException e) {
			consoleWrite("Error " + e.getMessage()); //$NON-NLS-1$
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return result;
	}

	/**
	 * Downloads a file from a URL, API V2
	 * 
	 * @param fileURL     HTTP URL of the file to be downloaded
	 * @param appid       Application id
	 * @param environment environment (acceptance, production, etc)
	 * @param saveDir     path of the directory to save the file
	 * @param doDownload  if false the file is not downloaded but only the filename
	 *                    is returned. For restore already downloaded file
	 * @return filename of downloaded file
	 * @throws IOException exception
	 */

	public String downloadFileV2(String appid, String projectId, String environment, String environmentId,
			String snapshotId, String archiveId, String saveDir, Boolean doDownload, Boolean includeDocuments)
			throws IOException {
		String result = ""; //$NON-NLS-1$
		URL url;
		try {
			// URL:
			// https://deploy.mendix.com/api/v2/apps/<ProjectId>/environments/<EnvironmentId>/snapshots/<SnapshotId>/archives/<ArchiveId>
			final String statusUrl = MXBACKUPAPIV2 + "/" + projectId + "/environments/" + environmentId + "/snapshots/"
					+ snapshotId + "/archives/" + archiveId;

			Preferences prefs = getPreferences();

			// always check HTTP response code first
			url = getArchiveStatus(statusUrl);
			if (url != null) {
				HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
				try {
					String fileName = "mendixbackup-" + appid + "-" + environment + "-" + archiveId + ".backup";
					String ext = includeDocuments ? "-doc" : ""; //$NON-NLS-1$ //$NON-NLS-2$
					Long maxCount = prefs.getLong(SIZEPREFIX + appid + ext, 0);
					setProgressMax(1000);

					consoleWrite("fileName = " + fileName); //$NON-NLS-1$
					consoleWrite("Downloading"); //$NON-NLS-1$

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
								if (c++ == 20) {
									c = 0;
									Long prog = Math.min(100, totalbytesRead);
									if (maxCount > 0) {
										prog = 1000 * totalbytesRead / maxCount;
									}
									setProgress(prog.intValue());
									setStyledText(totalbytesRead / (1024 * 1024) + " MB read"); //$NON-NLS-1$
								}
							}
							// store for next time progress bar
							prefs.putLong(SIZEPREFIX + appid + ext, totalbytesRead);
						} catch (IOException e) {
							consoleWrite("Error " + e.getMessage()); //$NON-NLS-1$
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
					consoleWrite("File downloaded"); //$NON-NLS-1$
					setProgress(0);
					result = saveFilePath;
				} finally {
					httpConn.disconnect();
				}
			} else {
				consoleWrite("No file to download. Server replied HTTP code: "/* TODO CDG + responseCode */); //$NON-NLS-1$
			}
		} catch (MalformedURLException e) {
			consoleWrite("Error " + e.getMessage()); //$NON-NLS-1$
			e.printStackTrace();
		} catch (FileNotFoundException e1) {
			consoleWrite("Error " + e1.getMessage()); //$NON-NLS-1$
			e1.printStackTrace();
		} catch (IOException e1) {
			consoleWrite("Error " + e1.getMessage()); //$NON-NLS-1$
			e1.printStackTrace();
		}

		return result;
	}

	/*
	 * Request the status until it is completed or failed or timed out. true if it
	 * is completed, false if failed or timed out
	 */
	private URL getArchiveStatus(String statusUrl) {
		OkHttpClient client = getClient(60);
		try {
			Request request = new Request.Builder().url(statusUrl) // $NON-NLS-1$
					.get().addHeader(MENDIX_USER_NAME, apiuser).addHeader(MENDIX_API_KEY, apikey).build();
			int retrycount = 0;
			Response response;
			// give the cached version the chance to update the status
			response = client.newCall(request).execute();
			String statusresult = response.body().string();
			JSONObject resultJson = new JSONObject(statusresult);
			String status = resultJson.getString("state");
			consoleWrite(resultJson.toString());
			int timeout = 1;
			while ((status.contentEquals("queued") || status.contentEquals("running")) && retrycount++ < MaxRetry) {
				consoleWrite("Backup is not ready in Mendix cloud, will retry in " + timeout + " seconds, please wait");
				TimeUnit.SECONDS.sleep(timeout);
				if (timeout < 40) {
					timeout *= 2;
				}
				response = client.newCall(request).execute();
				statusresult = response.body().string();
				resultJson = new JSONObject(statusresult);
				status = resultJson.getString("state");
				consoleWrite(resultJson.toString());
			}
			if (status.contentEquals("failed") || retrycount >= 10) {
				consoleWrite("Backup retry failed");
			}
			if (resultJson.has("url")) {
				URL url = new URL(resultJson.getString("url"));
				return url;
			} else {
				return null;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			consoleWrite(e.getMessage());
			return null;
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			consoleWrite(e.getMessage());
			return null;
		}
	}

	/**
	 * return short name for environment
	 * 
	 * @param environment either acceptance or production or test
	 * 
	 * @return accp, prod or test
	 */

	private String shortEnv(String environment) {
		String shortEnv = "unknown"; //$NON-NLS-1$
		if (environment != null && !environment.isEmpty()) {
			// default name
			shortEnv = environment;
			// map regular names
			final Map<String, String> envToShortEnv = new HashMap<String, String>();
			envToShortEnv.put("production", "prod"); //$NON-NLS-1$ //$NON-NLS-2$
			envToShortEnv.put("acceptance", "accp"); //$NON-NLS-1$ //$NON-NLS-2$
			envToShortEnv.put("test", "test"); //$NON-NLS-1$ //$NON-NLS-2$
			if (envToShortEnv.containsKey(environment.toLowerCase())) {
				shortEnv = envToShortEnv.get(environment.toLowerCase());
			}
		}
		return shortEnv;
	}

	/**
	 * stop all running threads etc.
	 */
	public void interrupt() {
		consoleWrite("Canceled"); //$NON-NLS-1$
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
	 * @param shell Swing shell
	 */
	public void setCursorWait(Shell shell) {
		if (cursor != null)
			cursor.dispose();

		cursor = new Cursor(display, SWT.CURSOR_WAIT);

		shell.setCursor(cursor);
	}

	/**
	 * set the cursor normal, users knows that the process is ready
	 * 
	 * @param shell Swing shell
	 */

	public void setCursorDefault(Shell shell) {
		if (cursor != null) {
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
				String appid = apps.getJSONObject(AppIndexByListIndex(selectedApp)).getString("ProjectId"); //$NON-NLS-1$
				if (appid != null && !appid.isEmpty()) {
					java.awt.Desktop.getDesktop().browse(new URI(path + appid));
				}
			}
		} catch (IOException e1) {
			consoleWrite("Error " + e1.getMessage()); //$NON-NLS-1$
		} catch (URISyntaxException e1) {
			consoleWrite("Error " + e1.getMessage()); //$NON-NLS-1$
		}
	}

	/**
	 * get the list of recently used items (names), which is created by addMRU
	 * 
	 * @return list of strings with MRU items, can be empty
	 */

	public String[] getMRUItems() {
		Preferences prefs = getPreferences();

		ArrayList<String> mrulist = new ArrayList<String>();
		for (int i = 0; i < MAX_MRU; i++) {
			int appindex = prefs.getInt(MRUPREFIX + i, -1);
			if (appindex >= 0) {
				String name = apps.getJSONObject(appindex).getString("Name"); //$NON-NLS-1$
				if (!mrulist.contains(name)) {
					mrulist.add(name);
				}
			}
		}
		return mrulist.toArray(new String[mrulist.size()]);
	}

	/**
	 * Get the preferences storage
	 * 
	 * @return Preferences
	 */
	private Preferences getPreferences() {
		return Preferences.userNodeForPackage(MendixUtil.class);
	}

	/**
	 * Add an app to the MRU list, max 6 items. Latest item is not put on top.
	 * 
	 * @param appindex index of application
	 */

	public void addMRU(int appindex) {
		Preferences prefs = getPreferences();

		// shift them up.
		for (int i = MAX_MRU; i >= 0; i--) {
			prefs.putInt(MRUPREFIX + i, prefs.getInt(MRUPREFIX + (i - 1), -1));
		}
		prefs.putInt(MRUPREFIX + "0", appindex); //$NON-NLS-1$
	}

	/**
	 * Clear the MRU list.
	 */

	public void clearMRU() {
		Preferences prefs = getPreferences();
		for (int i = MAX_MRU; i >= 0; i--) {
			prefs.putInt(MRUPREFIX + i, -1);
		}
	}

	/**
	 * Parse the XML date from JSON Returns Date
	 */

	public java.util.Date parseJSONDate(String jsonDate) {
		if (jsonDate != null && !jsonDate.isEmpty()) {
			try {
				SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"); //$NON-NLS-1$
				return parser.parse(jsonDate);
			} catch (Exception e) {
				consoleWrite("Error parsing date " + jsonDate); //$NON-NLS-1$
				return null;
			}
		} else {
			return null;
		}

	}

}