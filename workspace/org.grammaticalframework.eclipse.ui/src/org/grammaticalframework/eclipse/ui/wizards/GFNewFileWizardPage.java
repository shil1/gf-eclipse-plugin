/**
 * GF Eclipse Plugin
 * http://www.grammaticalframework.org/eclipse/
 * John J. Camilleri, 2011
 * 
 * The research leading to these results has received funding from the
 * European Union's Seventh Framework Programme (FP7/2007-2013) under
 * grant agreement n° FP7-ICT-247914.
 */
package org.grammaticalframework.eclipse.ui.wizards;

import java.util.ArrayList;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.bindings.keys.KeyStroke;
import org.eclipse.jface.bindings.keys.ParseException;
import org.eclipse.jface.dialogs.IDialogPage;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.fieldassist.SimpleContentProposalProvider;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ContainerSelectionDialog;

/**
 * The "New" wizard page allows setting the container for the new file as well
 * as the file name. The page will only accept file name without the extension
 * OR with the extension that matches the expected one (gf).
 */

public class GFNewFileWizardPage extends WizardPage {
	
	/**
	 * The Constant PAGE_NAME.
	 */
	private static final String PAGE_NAME = "GF Module";
	
	/**
	 * The Constant PAGE_DESCRIPTION.
	 */
	private static final String PAGE_DESCRIPTION = "This wizard creates a new GF module source file (*.gf)"; //$NON-NLS-1$

	/**
	 * The container text.
	 */
	private Text containerText;
	
	/**
	 * The mod name text.
	 */
	private Text modNameText;
	
	/**
	 * The mod incomplete button.
	 */
	private Button modIncompleteButton;
	
	/**
	 * The mod type combo drop down.
	 */
	private Combo modTypeComboDropDown;
	
	/**
	 * The mod of text.
	 */
	private Text modOfText;
	
	/**
	 * The mod instantiates text.
	 */
	private Text modExtendsText,modOpensText, modFunctorText, modInstantiatesText;

	/**
	 * The selection.
	 */
	private ISelection selection;

	/**
	 * Constructor for SampleNewWizardPage.
	 *
	 * @param selection the selection
	 */
	public GFNewFileWizardPage(ISelection selection) {
		super(PAGE_NAME);
		setTitle(PAGE_NAME);
		setDescription(PAGE_DESCRIPTION);
		this.selection = selection;
	}

	/**
	 * Creates the control.
	 *
	 * @param parent the parent
	 * @see IDialogPage#createControl(Composite)
	 */
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);
		GridLayout layout = new GridLayout();
		container.setLayout(layout);
		layout.numColumns = 3;
		layout.verticalSpacing = 5;
		
		// Container
		new Label(container, SWT.NULL).setText("&Save to:");

		containerText = new Text(container, SWT.BORDER | SWT.SINGLE);
		containerText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		containerText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				dialogChanged();
			}
		});

		Button button = new Button(container, SWT.PUSH);
		button.setText("Browse...");
		button.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				handleBrowse();
			}
		});
		
		// Module name
		new Label(container, SWT.NULL).setText("&Module name:");
		modNameText = new Text(container, SWT.BORDER | SWT.SINGLE);
		modNameText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
		modNameText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				dialogChanged();
			}
		});
		new Label(container, SWT.NULL); // Skip cell!
		
		// Incomplete?
		modIncompleteButton = new Button(container, SWT.CHECK);
		modIncompleteButton.setText("&Incomplete");
	    modIncompleteButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.TOP, true, false, 3, 1));
		
	    // Type
	    modTypeComboDropDown = new Combo(container, SWT.DROP_DOWN | SWT.BORDER | SWT.READ_ONLY);
	    modTypeComboDropDown.setItems(new String[] {
    	    "abstract",
    	    "resource",
    	    "interface",
    	    "concrete of",
    	    "instance of",
	    });
	    modTypeComboDropDown.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				if (modTypeComboDropDown.getText().equals("concrete of") || modTypeComboDropDown.getText().equals("instance of")) {
					modOfText.setEnabled(true);
					dialogChanged();
				} else {
					modOfText.setText("");
					modOfText.setEnabled(false);
				}
			}
			public void widgetDefaultSelected(SelectionEvent e) {
				widgetSelected(e);
			}
		});
	    
	    
	    // Ref: http://www.vogella.de/articles/EclipseRCP/article.html#fieldassist
		modOfText = new Text(container, SWT.BORDER | SWT.SINGLE);
		modOfText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

	    // Get suggestions...
		ArrayList<String> suggestions = getFileList();
		KeyStroke keystroke = null;
		try {
			keystroke = KeyStroke.getInstance("Ctrl+Space");
		} catch (ParseException _) {
		}		
		@SuppressWarnings("unused")
		ContentProposalAdapter adapter = new ContentProposalAdapter(
				modOfText,
				new TextContentAdapter(),
				new SimpleContentProposalProvider(suggestions.toArray(new String[]{})),
				keystroke, null);

		// Create the decoration for the text UI component
		final ControlDecoration deco = new ControlDecoration(modOfText, SWT.TOP | SWT.RIGHT);
		Image image = FieldDecorationRegistry.getDefault()
				.getFieldDecoration(FieldDecorationRegistry.DEC_CONTENT_PROPOSAL)
				.getImage();
		deco.setDescriptionText("Use Ctrl+Space to see possible values");
			deco.setImage(image);
		
		modOfText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				dialogChanged();
			}
		});

		// Init
		modTypeComboDropDown.select(0);
	    modOfText.setEnabled(false);
		
		new Label(container, SWT.NULL); // Skip cell!

		// Extends
		new Label(container, SWT.NULL).setText("&Extends:");
		modExtendsText = new Text(container, SWT.BORDER | SWT.SINGLE);
		modExtendsText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		modExtendsText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				dialogChanged();
			}
		});
		
		// Functor Instantiations
		new Label(container, SWT.NULL).setText("&Functor:");
		modFunctorText = new Text(container, SWT.BORDER | SWT.SINGLE);
		modFunctorText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
		modFunctorText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				dialogChanged();
			}
		});
		new Label(container, SWT.NULL); // Skip cell
		
		new Label(container, SWT.NULL).setText("&With:");
		modInstantiatesText = new Text(container, SWT.BORDER | SWT.SINGLE);
		modInstantiatesText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		modInstantiatesText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				dialogChanged();
			}
		});

		// Opens
		new Label(container, SWT.NULL).setText("&Opens:");
		modOpensText = new Text(container, SWT.BORDER | SWT.SINGLE);
		modOpensText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		modOpensText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				dialogChanged();
			}
		});
		
		// ...		

		initialize();
		setControl(container);
	}
	
	/**
	 * Recursively find all files in the workspace, in a flat list to be used as suggestions.
	 *
	 * @return the file list
	 */
	private ArrayList<String> getFileList() {
		ArrayList<String> suggestions = new ArrayList<String>();
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		traverseFileList(root, suggestions);
		return suggestions;
	}
	
	/**
	 * Traverse file list.
	 *
	 * @param resource the resource
	 * @param suggestions the suggestions
	 */
	private void traverseFileList(IResource resource, ArrayList<String> suggestions) {
		if (resource instanceof IFile) {
			IFile file = (IFile)resource;
			try {
				if (file.getFileExtension().equalsIgnoreCase("gf")) {
					suggestions.add( resource.getName().substring(0, resource.getName().length()-3) );
				}
			} catch (NullPointerException e) {
				// there was no file extension
			}
		} else if (resource instanceof IContainer) {
			try {
				for (IResource member : ((IContainer)resource).members()) {
					traverseFileList(member, suggestions);
				}
			} catch (CoreException e) {
				// No problem
			}
		}
	}
	

	/**
	 * Gets the mod is incomplete.
	 *
	 * @return the mod is incomplete
	 */
	protected Boolean getModIsIncomplete() {
		return modIncompleteButton.getSelection();
	}
	
	/**
	 * Gets the module type.
	 *
	 * @return the module type
	 */
	protected String getModuleType() {
		if (modTypeComboDropDown.getText().endsWith("of"))
			return modTypeComboDropDown.getText().substring(0, 8); // concrete & instance both 8 chars long
		else
			return modTypeComboDropDown.getText();
	}
	
	/**
	 * Gets the mod of.
	 *
	 * @return the mod of
	 */
	protected String getModOf() {
		return modOfText.getText();
	}

	/**
	 * Gets the module extends.
	 *
	 * @return the module extends
	 */
	protected String getModuleExtends() {
		return modExtendsText.getText();
	}
	
	/**
	 * Gets the module functor.
	 *
	 * @return the module functor
	 */
	protected String getModuleFunctor() {
		return modFunctorText.getText();
	}
	
	/**
	 * Gets the module instantiates.
	 *
	 * @return the module instantiates
	 */
	protected String getModuleInstantiates() {
		return modInstantiatesText.getText();
	}
	
	/**
	 * Gets the module opens.
	 *
	 * @return the module opens
	 */
	protected String getModuleOpens() {
		return modOpensText.getText();
	}
	
	/**
	 * Gets the module name.
	 *
	 * @return the module name
	 */
	protected String getModuleName() {
		return modNameText.getText();
	}

	/**
	 * Gets the container name.
	 *
	 * @return the container name
	 */
	protected String getContainerName() {
		return containerText.getText();
	}

	/**
	 * Tests if the current workbench selection is a suitable container to use.
	 */
	private void initialize() {
		if (selection != null && selection.isEmpty() == false
				&& selection instanceof IStructuredSelection) {
			IStructuredSelection ssel = (IStructuredSelection) selection;
			if (ssel.size() > 1)
				return;
			Object obj = ssel.getFirstElement();
			if (obj instanceof IResource) {
				IContainer container;
				if (obj instanceof IContainer)
					container = (IContainer) obj;
				else
					container = ((IResource) obj).getParent();
				containerText.setText(container.getFullPath().toString());
			}
		}
		modNameText.setText("untitled");
	}

	/**
	 * Uses the standard container selection dialog to choose the new value for
	 * the container field.
	 */

	private void handleBrowse() {
		ContainerSelectionDialog dialog = new ContainerSelectionDialog(
				getShell(), ResourcesPlugin.getWorkspace().getRoot(), false,
				"Select new file container");
		if (dialog.open() == ContainerSelectionDialog.OK) {
			Object[] result = dialog.getResult();
			if (result.length == 1) {
				containerText.setText(((Path) result[0]).toString());
			}
		}
	}
	
	/**
	 * Dialog changed.
	 */
	private void dialogChanged() {
		IResource container = ResourcesPlugin.getWorkspace().getRoot().findMember(new Path(getContainerName()));
		
		String regexModName = "[a-zA-Z_][a-zA-Z0-9_']*";
		String regexFunctor = "[a-zA-Z_][a-zA-Z0-9_']*\\s*(\\s*-?\\s*\\[.*?\\])?";
		String regexExtends = "[a-zA-Z_][a-zA-Z0-9_']*\\s*(\\s*-?\\s*\\[.*?\\])?(\\s*,\\s*[a-zA-Z_][a-zA-Z0-9_']*\\s*(-?\\s*\\[.*?\\])?\\s*)*";
		String regexOpens = "([a-zA-Z_][a-zA-Z0-9_']*|\\(\\s*[a-zA-Z_][a-zA-Z0-9_']*\\s*(=\\s*[a-zA-Z_][a-zA-Z0-9_']*\\s*)?\\))\\s*(\\s*,\\s*([a-zA-Z_][a-zA-Z0-9_']*|\\(\\s*[a-zA-Z_][a-zA-Z0-9_']*\\s*(=\\s*[a-zA-Z_][a-zA-Z0-9_']*\\s*)?\\))\\s*)*";
		
		// Container / location
		if (getContainerName().length() == 0) {
			updateStatus("File container must be specified");
			return;
		}
		if (container == null || (container.getType() & (IResource.PROJECT | IResource.FOLDER)) == 0) {
			updateStatus("File container must exist");
			return;
		}
		if (!container.isAccessible()) {
			updateStatus("Project must be writable");
			return;
		}

		// Module name
		String moduleName = getModuleName();
		if (moduleName.length() == 0) {
			updateStatus("Module name must be specified");
			return;
		}
		if (!moduleName.matches(regexModName)) {
			updateStatus("Module name is invalid");
			return;
		}

		// Concrete / Instance of
		if (modTypeComboDropDown.getText().equals("concrete of") && getModOf().isEmpty()) {
			updateStatus("Concrete of ... must be specified");
			return;
		}
		if (modTypeComboDropDown.getText().equals("instance of") && getModOf().isEmpty()) {
			updateStatus("Instance of ... must be specified");
			return;
		}
		
		
		// Extends, Functor, Instantiates, Opens
		if (!getModuleExtends().isEmpty() && !getModuleExtends().matches(regexExtends)) {
			updateStatus("Extends field is invalid");
			return;
		}
		if (!getModuleInstantiates().isEmpty() && !getModuleInstantiates().matches(regexOpens)) {
			updateStatus("Instantiates field is invalid");
			return;
		}
		if (!getModuleOpens().isEmpty() && !getModuleOpens().matches(regexOpens)) {
			updateStatus("Opens field is invalid");
			return;
		}
		if (!getModuleFunctor().isEmpty() && !getModuleFunctor().matches(regexFunctor)) {
			updateStatus("Functor field is invalid");
			return;
		}

		updateStatus(null);
		
	}

	/**
	 * Update status.
	 *
	 * @param message the message
	 */
	private void updateStatus(String message) {
		setErrorMessage(message);
		setPageComplete(message == null);
	}

}