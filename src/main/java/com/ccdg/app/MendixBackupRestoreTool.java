package main.java.com.ccdg.app;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Button;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.custom.PopupList;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Label;
import org.eclipse.wb.swt.SWTResourceManager;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;

public class MendixBackupRestoreTool {

	protected Shell shell;
	protected Display display;
	protected MendixUtil mendixUtil;
	private List consolelist;
	private Text filterText;

	/**
	 * Launch the application.
	 * 
	 * @param args command line args, not implemented
	 */
	public static void main(String[] args) {
		try {
			/*Locale locale = new Locale("nl", "NL");
			Locale.setDefault(locale);*/			
			MendixBackupRestoreTool window = new MendixBackupRestoreTool();
			window.open();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Open the window.
	 */
	public void open() {
		display = Display.getDefault();
		createContents();
		shell.open();
		shell.layout();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	/**
	 * Create contents of the window.
	 */
	protected void createContents() {
		shell = new Shell();

		shell.setMinimumSize(new Point(200, 39));
		shell.setImage(SWTResourceManager.getImage(MendixBackupRestoreTool.class, "/images/mendix.png")); //$NON-NLS-1$
		shell.setSize(1024, 635);
		shell.setText(Messages.getString("MendixBackupRestoreTool.MendixBackupTool")); //$NON-NLS-1$
		shell.setLayout(new FormLayout());

		// list for messages
		consolelist = new List(shell, SWT.BORDER | SWT.V_SCROLL);
		consolelist.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.stateMask == SWT.CTRL && (e.keyCode == 'c' || e.keyCode == 'C')) {
					Clipboard clipboard = new Clipboard(Display.getDefault());
					clipboard.setContents(new Object [] {getTextFromSelectedRows()}, new Transfer[] { TextTransfer.getInstance() });
					clipboard.dispose();
				} 
				// otherwise do noting c will change focus
				e.doit = false;
			}

			private String getTextFromSelectedRows() {
				String[] selection = consolelist.getSelection();
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < selection.length; i++) {
					sb.append(selection[i]); 
				}
				return sb.toString();
			}
		});
		FormData fd_consolelist = new FormData();
		fd_consolelist.left = new FormAttachment(0, 10);
		consolelist.setLayoutData(fd_consolelist);

		// single line to copy text.
		StyledText styledText = new StyledText(shell, SWT.BORDER);
		fd_consolelist.bottom = new FormAttachment(styledText, -6);
		fd_consolelist.right = new FormAttachment(styledText, 0, SWT.RIGHT);
		FormData fd_styledText = new FormData();
		fd_styledText.right = new FormAttachment(100, -14);
		fd_styledText.left = new FormAttachment(0, 10);
		styledText.setLayoutData(fd_styledText);


		// progressbar
		ProgressBar progressBar = new ProgressBar(shell, SWT.NONE);
		fd_styledText.bottom = new FormAttachment(100, -36);
		FormData fd_progressBar = new FormData();
		fd_progressBar.bottom = new FormAttachment(100, -7);
		fd_progressBar.right = new FormAttachment(100, -50);
		fd_progressBar.left = new FormAttachment(0, 10);
		progressBar.setLayoutData(fd_progressBar);

		Label lblApplications = new Label(shell, SWT.NONE);


		final List applist = new List(shell, SWT.BORDER | SWT.V_SCROLL  );
		FormData fd_applist = new FormData();
		fd_applist.right = new FormAttachment(32);
		fd_applist.left = new FormAttachment(0, 10);
		applist.setLayoutData(fd_applist);

		List backuplist = new List(shell, SWT.BORDER | SWT.V_SCROLL);
		FormData fd_backuplist = new FormData();
		fd_backuplist.bottom = new FormAttachment(applist, 0, SWT.BOTTOM);
		fd_backuplist.top = new FormAttachment(applist, 0, SWT.TOP);
		backuplist.setLayoutData(fd_backuplist);



		mendixUtil = new MendixUtil(display,  "postgres", "postgres", consolelist, styledText, progressBar, applist, backuplist); //$NON-NLS-1$ //$NON-NLS-2$


		// close down all threads if the program exits
		shell.addShellListener(new ShellListener() {

			@Override
			public void shellActivated(ShellEvent arg0) {
			}

			@Override
			public void shellClosed(ShellEvent arg0) {
				mendixUtil.interrupt();
			}

			@Override
			public void shellDeactivated(ShellEvent arg0) {
			}

			@Override
			public void shellDeiconified(ShellEvent arg0) {
			}

			@Override
			public void shellIconified(ShellEvent arg0) {
			}
		});	
		final Button btnCreateBackupOn = new Button(shell, SWT.NONE);
		btnCreateBackupOn.setEnabled(false);

		Button btnIncludeDocuments = new Button(shell, SWT.CHECK);
		FormData fd_btnIncludeDocuments = new FormData();
		fd_btnIncludeDocuments.top = new FormAttachment(btnCreateBackupOn, 5, SWT.TOP);
		fd_btnIncludeDocuments.left = new FormAttachment(btnCreateBackupOn, 6);
		btnIncludeDocuments.setLayoutData(fd_btnIncludeDocuments);
		btnIncludeDocuments.setText(Messages.getString("MendixBackupRestoreTool.IncludeDocuments")); //$NON-NLS-1$


		final List environmentlist = new List(shell, SWT.BORDER);

		Label lblEnvironment = new Label(shell, SWT.NONE);
		FormData fd_environmentlist = new FormData();
		fd_environmentlist.top = new FormAttachment(applist, 0, SWT.TOP);
		fd_environmentlist.bottom = new FormAttachment(applist, 0, SWT.BOTTOM);
		fd_environmentlist.right = new FormAttachment(backuplist, -25);
		fd_environmentlist.left = new FormAttachment(applist, 25);
		environmentlist.setLayoutData(fd_environmentlist);


		FormData fd_lblEnvironment = new FormData();
		fd_lblEnvironment.right = new FormAttachment(environmentlist, 0, SWT.RIGHT);
		fd_lblEnvironment.left = new FormAttachment(environmentlist, 0, SWT.LEFT);
		fd_lblEnvironment.top = new FormAttachment(lblApplications, 0, SWT.TOP);
		lblEnvironment.setLayoutData(fd_lblEnvironment);
		lblEnvironment.setText(Messages.getString("MendixBackupRestoreTool.Environment")); //$NON-NLS-1$
		//btnTest.setSelection(true);

		Button btnSettings = new Button(shell, SWT.NONE);
		FormData fd_btnSettings = new FormData();
		fd_btnSettings.right = new FormAttachment(100, -14);
		fd_btnSettings.top = new FormAttachment(0, 10);
		btnSettings.setLayoutData(fd_btnSettings);
		btnSettings.setImage(SWTResourceManager.getImage(MendixBackupRestoreTool.class, "/images/wrench.png")); //$NON-NLS-1$
		btnSettings.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				SettingsDialog dialog = new SettingsDialog(shell, SWT.APPLICATION_MODAL, mendixUtil);
				Point pt = display.getCursorLocation();
				pt.x = pt.x - dialog.getParent().getBounds().width / 5 * 4;
				dialog.setLocation(pt);
				dialog.open();
			}
		});
		btnSettings.setText(Messages.getString("MendixBackupRestoreTool.Settings")); //$NON-NLS-1$

		Button btnDownloadBackup = new Button(shell, SWT.NONE);
		fd_applist.bottom = new FormAttachment(btnDownloadBackup, -4);
		/*fd_btnRestoreBackup.bottom = new FormAttachment(consolelist, -6);
		fd_btnRestoreBackup.left = new FormAttachment(btnDownloadBackup, 6);
		 */
		btnDownloadBackup.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				mendixUtil.GetBackupLinkDownloadAndRestore(applist.getSelectionIndex(), backuplist.getSelectionIndex(), backuplist, environmentlist.getItem(environmentlist.getSelectionIndex()), true, false, btnIncludeDocuments.getSelection());
			}
		});



		Button btnGetProjectsFrom = new Button(shell, SWT.NONE);
		fd_applist.top = new FormAttachment(0, 102);
		//fd_lblEnvironment.left = new FormAttachment(btnGetProjectsFrom, 20);
		FormData fd_btnGetProjectsFrom = new FormData();
		fd_btnGetProjectsFrom.bottom = new FormAttachment(applist, -6);
		fd_btnGetProjectsFrom.left = new FormAttachment(applist, -34);
		fd_btnGetProjectsFrom.right = new FormAttachment(lblEnvironment, -25);
		btnGetProjectsFrom.setLayoutData(fd_btnGetProjectsFrom);
		btnGetProjectsFrom.setImage(SWTResourceManager.getImage(MendixBackupRestoreTool.class, "/images/refresh.png")); 

		btnGetProjectsFrom.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				mendixUtil.setCursorWait(shell);
				try {
					mendixUtil.GetAppList(applist, filterText.getText());
				} finally {
					mendixUtil.setCursorDefault(shell);
				}

			}
		});

		final Button btnGetBackup = new Button(shell, SWT.NONE);
		fd_consolelist.top = new FormAttachment(47, 16);
		FormData fd_btnGetBackup = new FormData();
		fd_btnGetBackup.bottom = new FormAttachment(consolelist, -6);
		fd_btnGetBackup.left = new FormAttachment(consolelist, 0, SWT.LEFT);
		btnGetBackup.setLayoutData(fd_btnGetBackup);
		btnGetBackup.setEnabled(false);

		// restore only
		Button btnRestoreBackup = new Button(shell, SWT.NONE);
		btnRestoreBackup.setToolTipText(Messages.getString("MendixBackupRestoreTool.Restore")); //$NON-NLS-1$


		Button btnRefreshBackups = new Button(shell, SWT.NONE);
		FormData fd_btnRefreshBackups = new FormData();
		fd_btnRefreshBackups.right = new FormAttachment(backuplist, 0, SWT.RIGHT);
		fd_btnRefreshBackups.bottom = new FormAttachment(backuplist, -6);
		btnRefreshBackups.setLayoutData(fd_btnRefreshBackups);
		btnRefreshBackups.setImage(SWTResourceManager.getImage(MendixBackupRestoreTool.class, "/images/refresh.png")); //$NON-NLS-1$
		btnRefreshBackups.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (applist.getSelectionIndex() >= 0 ) {
					mendixUtil.GetBackupList(applist.getSelectionIndex(), environmentlist.getItem(environmentlist.getSelectionIndex()) );
					btnGetBackup.setEnabled(true);
					btnRestoreBackup.setEnabled(true);
				} else {
					mendixUtil.ShowMessage(Messages.getString("MendixBackupRestoreTool.getApps"), shell); //$NON-NLS-1$
				}
			}
		});

		backuplist.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				btnGetBackup.setEnabled(true);
				btnRestoreBackup.setEnabled(true);
				btnDownloadBackup.setEnabled(true);
			}
		});


		// button Download and restore
		btnGetBackup.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				mendixUtil.GetBackupLinkDownloadAndRestore(applist.getSelectionIndex(), backuplist.getSelectionIndex(), backuplist, environmentlist.getItem(environmentlist.getSelectionIndex()), true, true, btnIncludeDocuments.getSelection());
			}

		});
		btnGetBackup.setText(Messages.getString("MendixBackupRestoreTool.DownloadRestore")); //$NON-NLS-1$

		btnRestoreBackup.setEnabled(false);
		btnRestoreBackup.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				mendixUtil.GetBackupLinkDownloadAndRestore(applist.getSelectionIndex(), backuplist.getSelectionIndex(), backuplist, environmentlist.getItem(environmentlist.getSelectionIndex()), false, true, btnIncludeDocuments.getSelection());
			}
		});
		btnRestoreBackup.setText(Messages.getString("MendixBackupRestoreTool.RestoreBackup")); //$NON-NLS-1$

		environmentlist.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				btnCreateBackupOn.setEnabled(true);
				if (environmentlist.getSelectionIndex() >= 0 && environmentlist.getSelectionIndex() < environmentlist.getItemCount()) {
					mendixUtil.setCursorWait(shell);
					try {
						if (mendixUtil.GetBackupList(applist.getSelectionIndex(), environmentlist.getItem(environmentlist.getSelectionIndex()))) {
							backuplist.select(0);
							btnGetBackup.setEnabled(true);
							btnRestoreBackup.setEnabled(true);
							btnDownloadBackup.setEnabled(true);
						}
					} finally {
						mendixUtil.setCursorDefault(shell);
					}				
				}
			}
		});		


		// label Mendix Backup Tool
		Label lblMendixBackupTool = new Label(shell, SWT.NONE);
		fd_btnGetProjectsFrom.top = new FormAttachment(lblMendixBackupTool, 4);
		fd_btnSettings.left = new FormAttachment(100, -130);
		FormData fd_lblMendixBackupTool = new FormData();
		fd_lblMendixBackupTool.bottom = new FormAttachment(0, 68);
		fd_lblMendixBackupTool.right = new FormAttachment(0, 789);
		fd_lblMendixBackupTool.top = new FormAttachment(0, 10);
		fd_lblMendixBackupTool.left = new FormAttachment(0, 10);
		lblMendixBackupTool.setLayoutData(fd_lblMendixBackupTool);
		lblMendixBackupTool.setFont(SWTResourceManager.getFont("Segoe UI", 23, SWT.BOLD)); 
		lblMendixBackupTool.setText(Messages.getString("MendixBackupRestoreTool.MendixBackupRestoreTool")); //$NON-NLS-1$

		applist.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				// clear all lists and selections
				btnGetBackup.setEnabled(false);
				btnRestoreBackup.setEnabled(false);
				btnDownloadBackup.setEnabled(false);
				btnCreateBackupOn.setEnabled(false);
				backuplist.removeAll();
				mendixUtil.getEnvironmentList(applist.getSelectionIndex(), environmentlist);
			}
		});		

		// label applications
		FormData fd_lblApplications = new FormData();
		fd_lblApplications.right = new FormAttachment(0, 106);
		fd_lblApplications.top = new FormAttachment(0, 74);
		fd_lblApplications.left = new FormAttachment(0, 10);
		lblApplications.setLayoutData(fd_lblApplications);
		lblApplications.setText(Messages.getString("MendixBackupRestoreTool.Applications")); //$NON-NLS-1$

		Button btnStop = new Button(shell, SWT.NONE);
		FormData fd_btnStop = new FormData();
		fd_btnStop.bottom = new FormAttachment(100);
		fd_btnStop.right = new FormAttachment(100, -14);
		fd_btnStop.top = new FormAttachment(styledText, 6);
		btnStop.setLayoutData(fd_btnStop);
		btnStop.setImage(SWTResourceManager.getImage(MendixBackupRestoreTool.class, "/images/stop.png")); //$NON-NLS-1$
		btnDownloadBackup.setText(Messages.getString("MendixBackupRestoreTool.DownloadBackup")); //$NON-NLS-1$
		btnDownloadBackup.setEnabled(false);
		FormData fd_btnDownloadBackup = new FormData();
		//fd_btnDownloadBackup.right = new FormAttachment(100, -645);
		fd_btnDownloadBackup.left = new FormAttachment(btnGetBackup, 6);
		fd_btnDownloadBackup.bottom = new FormAttachment(consolelist, -6);
		btnDownloadBackup.setLayoutData(fd_btnDownloadBackup);


		FormData fd_btnRestoreBackup = new FormData();
		fd_btnRestoreBackup.bottom = new FormAttachment(consolelist, -6);
		fd_btnRestoreBackup.left = new FormAttachment(btnDownloadBackup, 6);
		btnRestoreBackup.setLayoutData(fd_btnRestoreBackup);

		Button btnStories = new Button(shell, SWT.NONE);
		btnStories.setText(Messages.getString("MendixBackupRestoreTool.Stories")); //$NON-NLS-1$
		btnStories.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				mendixUtil.OpenBrowser("https://sprintr.home.mendix.com/link/capture/", applist.getSelectionIndex()); //$NON-NLS-1$

			}
		});
		btnStories.setImage(null);
		FormData fd_btnStories = new FormData();
		fd_btnStories.left = new FormAttachment(backuplist, 22);
		fd_btnStories.right = new FormAttachment(100, -14);
		btnStories.setLayoutData(fd_btnStories);

		Button btnEnvironment = new Button(shell, SWT.NONE);
		fd_btnStories.top = new FormAttachment(btnEnvironment, 6);
		fd_backuplist.left = new FormAttachment(53);
		fd_backuplist.right = new FormAttachment(100, -157);
		btnEnvironment.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				mendixUtil.OpenBrowser("https://cloud.home.mendix.com/link/deploy/", applist.getSelectionIndex()); //$NON-NLS-1$
			}
		});

		filterText = new Text(shell, SWT.BORDER);


		btnEnvironment.setText(Messages.getString("MendixBackupRestoreTool.Environment")); //$NON-NLS-1$
		btnEnvironment.setImage(null);
		FormData fd_btnEnvironment = new FormData();
		fd_btnEnvironment.left = new FormAttachment(backuplist, 22);
		fd_btnEnvironment.right = new FormAttachment(100, -14);
		fd_btnEnvironment.top = new FormAttachment(applist, -1, SWT.TOP);
		btnEnvironment.setLayoutData(fd_btnEnvironment);

		Button btnTeamServer = new Button(shell, SWT.NONE);
		btnTeamServer.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				mendixUtil.OpenBrowser("https://sprintr.home.mendix.com/link/develop/", applist.getSelectionIndex()); //$NON-NLS-1$
			}
		});
		btnTeamServer.setText(Messages.getString("MendixBackupRestoreTool.TeamServer")); //$NON-NLS-1$
		btnTeamServer.setImage(null);
		FormData fd_btnTeamServer = new FormData();
		fd_btnTeamServer.left = new FormAttachment(backuplist, 22);
		fd_btnTeamServer.right = new FormAttachment(100, -14);
		fd_btnTeamServer.top = new FormAttachment(btnStories, 6);
		btnTeamServer.setLayoutData(fd_btnTeamServer);

		Label lblGoTo = new Label(shell, SWT.NONE);
		lblGoTo.setText(Messages.getString("MendixBackupRestoreTool.Goto")); //$NON-NLS-1$
		FormData fd_lblGoTo = new FormData();
		fd_lblGoTo.right = new FormAttachment(100, -18);
		fd_lblGoTo.top = new FormAttachment(0, 74);
		lblGoTo.setLayoutData(fd_lblGoTo);

		Label lblBackups = new Label(shell, SWT.NONE);
		fd_btnRefreshBackups.left = new FormAttachment(lblBackups, 6);
		lblBackups.setText(Messages.getString("MendixBackupRestoreTool.Backups")); //$NON-NLS-1$
		FormData fd_lblBackups = new FormData();
		fd_lblBackups.top = new FormAttachment(lblMendixBackupTool, 6);
		fd_lblBackups.right = new FormAttachment(backuplist, -40, SWT.RIGHT);
		fd_lblBackups.left = new FormAttachment(backuplist, 0, SWT.LEFT);
		lblBackups.setLayoutData(fd_lblBackups);

		Button MRUbutton = new Button(shell, SWT.NONE);
		MRUbutton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				// clear on press shift + click
				if ((e.stateMask & SWT.SHIFT) > 0) {
					mendixUtil.clearMRU();					
				} else {
					PopupList list = new PopupList(shell);
					String[] items = mendixUtil.getMRUItems();
					if (items.length  > 0) {
						list.setItems(items);
						Rectangle rect = new Rectangle(
								shell.getBounds().x + MRUbutton.getBounds().x, 
								shell.getBounds().y + MRUbutton.getBounds().y+MRUbutton.getBounds().height+30, 
								200, items.length);
						// open the list
						String selected = list.open(rect);
						if (selected != null && !selected.isEmpty()) {
							applist.setSelection(applist.indexOf(selected));
							mendixUtil.getEnvironmentList(applist.getSelectionIndex(), environmentlist);
						}
					}
				}
			}
		});
		MRUbutton.setImage(SWTResourceManager.getImage(MendixBackupRestoreTool.class, "/javax/swing/plaf/metal/icons/sortDown.png"));
		FormData fd_MRUbutton = new FormData();
		fd_MRUbutton.bottom = new FormAttachment(lblMendixBackupTool, -16);
		fd_MRUbutton.right = new FormAttachment(100, -720);
		MRUbutton.setLayoutData(fd_MRUbutton);

		btnCreateBackupOn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				mendixUtil.createBackup(applist.getSelectionIndex(), environmentlist.getItem(environmentlist.getSelectionIndex()));
			}
		});
		FormData fd_btnCreateBackupOn = new FormData();
		//fd_btnCreateBackupOn.right = new FormAttachment(100, -295);
		fd_btnCreateBackupOn.left = new FormAttachment(btnRestoreBackup, 6);
		fd_btnCreateBackupOn.bottom = new FormAttachment(consolelist, -6);
		btnCreateBackupOn.setLayoutData(fd_btnCreateBackupOn);
		btnCreateBackupOn.setText(Messages.getString("MendixBackupRestoreTool.CreateBackup")); //$NON-NLS-1$

		Button btnCloudBackups = new Button(shell, SWT.NONE);
		btnCloudBackups.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				mendixUtil.OpenBrowser("https://cloud.home.mendix.com/link/backups/", applist.getSelectionIndex());
			}
		});
		btnCloudBackups.setText(Messages.getString("MendixBackupRestoreTool.CloudBackups")); //$NON-NLS-1$
		btnCloudBackups.setImage(null);
		FormData fd_btnCloudBackups = new FormData();
		fd_btnCloudBackups.left = new FormAttachment(backuplist, 22);
		fd_btnCloudBackups.right = new FormAttachment(100, -14);
		fd_btnCloudBackups.top = new FormAttachment(btnTeamServer, 6);
		btnCloudBackups.setLayoutData(fd_btnCloudBackups);

		filterText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent arg0) {
				mendixUtil.restorePreferences(applist, filterText.getText()); //cdg
			}
		});
		FormData fd_filterText = new FormData();
		fd_filterText.right = new FormAttachment(btnGetProjectsFrom, -21);
		fd_filterText.left = new FormAttachment(lblApplications, 15);
		fd_filterText.top = new FormAttachment(lblApplications, 0, SWT.TOP);
		filterText.setLayoutData(fd_filterText);

		filterText.setFocus();
		btnStop.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				mendixUtil.interrupt();
			}
		});
	}

	protected String getEnvironment(Button btnProduction, Button btnAcceptance, Button ButtonTest) {
		if (btnProduction.getSelection()) {
			return Messages.getString("MendixBackupRestoreTool.Production"); //$NON-NLS-1$
		} 		
		if (btnAcceptance.getSelection()) {
			return Messages.getString("MendixBackupRestoreTool.Acceptance"); //$NON-NLS-1$
		}		
		if (ButtonTest.getSelection()) {
			return Messages.getString("MendixBackupRestoreTool.Test"); //$NON-NLS-1$
		}		
		return null;
	}
}
