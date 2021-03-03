package main.java.com.ccdg.app;

import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Combo;

public class SettingsDialog extends Dialog {

	private static final String APIDOCSURL = "https://docs.mendix.com/developerportal/mendix-profile/#api-key";
	protected Object result;
	protected Shell shlSettings;
	private Text apiUserText;
	private Text apiKeyText;
	private Label lblMendixApiKey;
	private Button btnSave;
	private Button btnCancel;
	private MendixUtil mendixUtil;
	private Point location;
	private Text postgresUsertext;
	private Text postgresPasswordText;
	private Label lblPostgresUser;
	private Label lblPostgresPassword;
	private Label lblPostgresDirectory;
	private Text PostgresDirectory;
	private Label lblPostgresPort;
	private Text PostgresPort;
	private Combo backupnamingCombo;
	private Label lblBackupNaming;
	private Label lblDownloadDirectort;
	private Text downloadDirectory;

	/**
	 * Create the dialog.
	 * @param parent Shell of the main program
	 * @param style style bits @see <a href="https://wiki.eclipse.org/SWT_Widget_Style_Bits">wiki</a>}
	 * @param mendixUtil {@link MendixUtil} object
	 */
	public SettingsDialog(Shell parent, int style, MendixUtil mendixUtil) {
		super(parent, style);
		setText("Setting");
		this.mendixUtil = mendixUtil;
	}

	/**
	 * Open the dialog.
	 * @return the result
	 */
	public Object open() {
		createContents();
		shlSettings.open();
		shlSettings.layout();
		Display display = getParent().getDisplay();
		while (!shlSettings.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		return result;
	}

	/**
	 * Create contents of the dialog.
	 */
	private void createContents() {
		shlSettings = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		shlSettings.setLocation(location);

		shlSettings.setSize(647, 410);
		shlSettings.setText("Settings");
		
		Label lblMenduxApiUser = new Label(shlSettings, SWT.NONE);
		lblMenduxApiUser.setBounds(18, 39, 135, 21);
		lblMenduxApiUser.setText("Mendix API User");
		
		apiUserText = new Text(shlSettings, SWT.BORDER);
		apiUserText.setBounds(207, 36, 394, 24);
		apiUserText.setText(mendixUtil.apiuser);

		
		lblBackupNaming = new Label(shlSettings, SWT.NONE);
		lblBackupNaming.setText("Backup naming");
		lblBackupNaming.setBounds(18, 267, 135, 21);

		
		apiKeyText = new Text(shlSettings, SWT.BORDER);
		apiKeyText.setToolTipText("Go to 'my profile' on the mendix site, select the settings icon, API Keys");
		apiKeyText.setBounds(207, 68, 394, 24);
		apiKeyText.setText(mendixUtil.apikey);
		
		lblMendixApiKey = new Label(shlSettings, SWT.NONE);
		lblMendixApiKey.setBounds(18, 71, 135, 21);
		lblMendixApiKey.setText("Mendix API Key");
		
		Link link = new Link(shlSettings, SWT.NONE);
		link.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				java.net.URI uri;
				try {
					uri = new java.net.URI(APIDOCSURL);
					java.awt.Desktop.getDesktop().browse(uri);
				} catch (URISyntaxException e1) {
					e1.printStackTrace();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		});
		link.setBounds(207, 100, 394, 21);
		link.setText("<a>https://docs.mendix.com/developerportal/mendix-profile/#api-key</a>");
		
		postgresUsertext = new Text(shlSettings, SWT.BORDER);
		postgresUsertext.setText(mendixUtil.username);
		postgresUsertext.setBounds(207, 132, 394, 24);
		
		postgresPasswordText = new Text(shlSettings, SWT.BORDER);
		postgresPasswordText.setText(mendixUtil.password);
		postgresPasswordText.setBounds(207, 164, 394, 24);
		
		lblPostgresUser = new Label(shlSettings, SWT.NONE);
		lblPostgresUser.setText("Postgres user");
		lblPostgresUser.setBounds(18, 135, 97, 21);
		
		lblPostgresPassword = new Label(shlSettings, SWT.NONE);
		lblPostgresPassword.setText("Postgres password");
		lblPostgresPassword.setBounds(18, 167, 135, 21);
		
		Label lblCopyrightChrisDe = new Label(shlSettings, SWT.NONE);
		lblCopyrightChrisDe.setToolTipText("");
		lblCopyrightChrisDe.setBounds(18, 341, 221, 23);
		lblCopyrightChrisDe.setText("V 3.2 - Chris de Gelder");
		
		lblPostgresDirectory = new Label(shlSettings, SWT.NONE);
		lblPostgresDirectory.setText("Postgres directory (no bin)");
		lblPostgresDirectory.setBounds(18, 199, 167, 24);
		
		PostgresDirectory = new Text(shlSettings, SWT.BORDER);
		if (mendixUtil.postgresDirectory != null) {
			PostgresDirectory.setText(mendixUtil.postgresDirectory);
		}
		PostgresDirectory.setBounds(207, 196, 394, 24);
		
		lblPostgresPort = new Label(shlSettings, SWT.NONE);
		lblPostgresPort.setText("Postgres port");
		lblPostgresPort.setBounds(18, 233, 135, 21);
		
		PostgresPort = new Text(shlSettings, SWT.BORDER);
		if (mendixUtil.postgresPort != null) {
			PostgresPort.setText(mendixUtil.postgresPort);
		}
		PostgresPort.setBounds(207, 230, 394, 24);
		
		lblDownloadDirectort = new Label(shlSettings, SWT.NONE);
		lblDownloadDirectort.setText("Download directory");
		lblDownloadDirectort.setBounds(18, 299, 135, 21);
		
		downloadDirectory = new Text(shlSettings, SWT.BORDER);
		downloadDirectory.setText(mendixUtil.downloadDirectory);
		downloadDirectory.setBounds(207, 296, 394, 24);		
		
		backupnamingCombo = new Combo(shlSettings, SWT.NONE);
		backupnamingCombo.setToolTipText("select the way the names of the backups are created.");
		backupnamingCombo.setItems(new String[] {"", "_{environment}", "_{environment}_{date}", "_{date}_{environment}", "_{date}"});
		backupnamingCombo.setBounds(207, 264, 394, 24);
		// set value from prefs
		backupnamingCombo.select( Arrays.asList(backupnamingCombo.getItems()).indexOf(mendixUtil.backupNaming) );
		
		btnSave = new Button(shlSettings, SWT.NONE);
		btnSave.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {

				// copy back to main screen
				mendixUtil.setUserAndKey(apiUserText.getText(), apiKeyText.getText(), 
						postgresUsertext.getText(),postgresPasswordText.getText(), PostgresDirectory.getText(),
						PostgresPort.getText(), downloadDirectory.getText());
				mendixUtil.setBackupNaming(backupnamingCombo.getText());
				shlSettings.close();
			}
		});
		btnSave.setBounds(445, 336, 75, 25);
		btnSave.setText("Save");
		
		btnCancel = new Button(shlSettings, SWT.NONE);
		btnCancel.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				shlSettings.close();				
			}
		});
		btnCancel.setBounds(526, 336, 75, 25);
		btnCancel.setText("Cancel");
	}

	public void setLocation(Point p) {
		this.location = p;
	}
}
