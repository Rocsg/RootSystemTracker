/*
 *
 */
package io.github.rocsg.gui;

import com.sun.management.OperatingSystemMXBean;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.Memory;
import ij.plugin.RGBStackMerge;
import ij.plugin.frame.PlugInFrame;
import ij.plugin.frame.RoiManager;
import io.github.rocsg.fijiyama.common.Timer;
import io.github.rocsg.fijiyama.common.*;
import io.github.rocsg.rsml.Node;
import io.github.rocsg.rsml.Root;
import io.github.rocsg.rsml.RootModel;
import org.apache.commons.io.FileUtils;
import org.scijava.vecmath.Point3d;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.rocsg.rsml.RSMLParser2DplusT.rootModelReadFromRsml;

/**
 * The Class RsmlExpert_Plugin.
 */
public class RsmlExpert_Plugin extends PlugInFrame implements KeyListener, ActionListener {


    /**
     * The Constant serialVersionUID.
     */
    /* Internal variables ******************************************************************************/
    private static final long serialVersionUID = 1L;
    /**
     * The Constant OK.
     */
    private static final int OK = 1;
    /**
     * The Constant UNDO.
     */
    private static final int UNDO = 2;
    /**
     * The Constant MOVE.
     */
    private static final int MOVE = 3;
    /**
     * The Constant REMOVE.
     */
    private static final int REMOVE = 4;
    /**
     * The Constant ADD.
     */
    private static final int ADD = 5;
    /**
     * The Constant SWITCH.
     */
    private static final int SWITCH = 6;
    /**
     * The Constant CREATE.
     */
    private static final int CREATE = 7;
    /**
     * The Constant EXTEND.
     */
    private static final int EXTEND = 8;
    /**
     * The Constant INFO.
     */
    private static final int INFO = 9;
    /**
     * The Constant CHANGE.
     */
    private static final int CHANGE = 10;
    /**
     * The Constant INFO.
     */
    private static final int SAVE = 11;
    /**
     * The Constant CHANGE.
     */
    private static final int RESAMPLE = 12;
    /**
     * The Constant BACKEXTEND.
     */
    private static final int BACKEXTEND = 13;

    private static final int FIT = 14;
    private static final int EXIT = 15;
    /**
     * The Constant all.
     */
    private static final int[] all = new int[]{OK, UNDO, MOVE, REMOVE, ADD, SWITCH, CREATE, EXTEND, INFO, CHANGE,
            SAVE, RESAMPLE, BACKEXTEND, FIT, EXIT};

    private final JButton buttonOk = new JButton("OK");
    private final JButton buttonUndo = new JButton("Undo last action");
    private final JButton buttonMove = new JButton("Move a point");
    private final JButton buttonRemove = new JButton("Remove a point");
    private final JButton buttonRefine = new JButton("Refine a segment");
    private final JButton buttonSwitch = new JButton("Switch a false cross");
    private final JButton buttonCreateLateral = new JButton("Create a new branch");
    private final JButton buttonExtend = new JButton("Extend a branch");
    private final JButton buttonBackExtend = new JButton("Backwards extension");
    private final JButton buttonInfo = new JButton("Information about a node and a branch");
    private final JButton buttonChange = new JButton("Change time");
    private final JButton buttonSave = new JButton("Save final edited RSML");
    private final JButton buttonResample = new JButton("Time-resampling");
    private final JButton buttonCreatePrimary = new JButton("Create a primary root");
    private final JButton buttonFit = new JButton("Fit roots curve");
    private final JButton buttonExit = new JButton("Exit");

    /**
     * The log area.
     */
    private final JTextArea logArea = new JTextArea("", 11, 10);
    /**
     * The zoom factor.
     */
    private final int zoomFactor = 2;

    private final int nMaxModifs = 500;
    /**
     * The user precision on click.
     */
    private final double USER_PRECISION_ON_CLICK = 20;
    private final boolean isDevModeActivated = false;
    private final String currentVersion = "v2.0.2-SNAPSHOT";
    /**
     * The Nt.
     */
    int Nt;
    /**
     * The data dir.
     */
    private String dataDir;
    /**
     * The registered stack.
     */
    private ImagePlus registeredStack = null;
    /**
     * The current image.
     */
    private ImagePlus currentImage = null;
    /**
     * The current model.
     */
    private RootModel currentModel = null;
    /**
     * The number of modifications that have been made
     */
    private int nModifs = 0;
    /**
     * The tab of modifications did
     */
    private String[][] tabModifs = null;
    /**
     * The img init size.
     */
    private ImagePlus imgInitSize;
    /**
     * The tab reg.
     */
    private ImagePlus[] tabReg;
    /**
     * The tab res.
     */
    private ImagePlus[] tabRes;
    /**
     * The t.
     */
    private Timer t;
    /**
     * The frame.
     */
    private JFrame frame;
    /**
     * The buttons panel.
     */
    private JPanel buttonsPanel;
    /**
     * The panel global.
     */
    private JPanel panelGlobal;
    /**
     * The ok clicked.
     */
    private boolean okClicked;
    /**
     * The count.
     */
    private int count = 0;
    private String stackPath;
    private String rsmlPath;
    private boolean toResize = true;


    private String version = "v2.0.2-SNAPSHOT - Release candidate";


    /**
     * Instantiates a new rsml expert plugin.
     */
    public RsmlExpert_Plugin() {
        super("");
    }

    /**
     * The main method.
     *
     * @param args the arguments
     */
    /* Plugin entry points for test/debug or run in production ******************************************************************/
    public static void main(String[] args) throws IOException {
        final ImageJ ij = new ImageJ();


        //createTif();
        //Path folderPath = Paths.get("C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Original_Data\\B73_R04_01\\");


        //RootModel rm = new RootModel();
        //rm.readRSMLNew("C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Original_Data\\B73_R04_01\\");
        RsmlExpert_Plugin plugin = new RsmlExpert_Plugin();
        //plugin.isDevModeActivated = true;
        plugin.run(null);//testDir);
    }

    public String getBoxName() {
        String[] tab;
        if (!this.stackPath.contains("\\")) {
            tab = this.stackPath.split("/");
        } else {
            tab = this.stackPath.split("\\\\");
        }
        return tab[tab.length - 2];
    }

    /**
     * Run.
     *
     * @param arg the arg
     */
    public void run(String arg) {
        this.startPlugin(arg);
    }

    /**
     * Start plugin.
     *
     * @param arg the directory containing the processing files of a box
     */
    /* Setup of plugin and GUI ************************************************************************************/
    public void startPlugin(String arg) {
        t = new Timer();

        //Choose an existing expertize, or initiate a new one
        if (arg != null && !arg.isEmpty() && new File(arg).exists()) {
            dataDir = arg;
        } else {
            dataDir = VitiDialogs.chooseDirectoryUI("Choose a boite directory", "Ok");
        }
        if (!new File(dataDir, "InfoRSMLExpert.csv").exists()) startNewExpertize();
        readInfoFile();
        t.mark();

        //Choose an existing expertize, or initiate a new one
        setupImageAndRsml();
        addLog(t.gather("Setup image and rsml took : "), 0);
        t.mark();

        startGui();
        welcomeAndInformAboutComputerCapabilities();
    }

    /**
     * Function to read the InfoRSMLExpert.csv file and get the stack and rsml paths
     */
    public void readInfoFile() {
        String[][] tab = VitimageUtils.readStringTabFromCsv(new File(dataDir, "InfoRSMLExpert.csv").getAbsolutePath().replace("\\", "/"));
        this.tabModifs = new String[500][nMaxModifs];
        for (String[] tabModif : tabModifs) Arrays.fill(tabModif, "");
        for (int i = 0; i < tab.length; i++) System.arraycopy(tab[i], 0, tabModifs[i], 0, tab[i].length);
        this.stackPath = tabModifs[0][0];
        this.rsmlPath = tabModifs[0][1];
        this.version = tabModifs[0][2];
    }

    public void writeInfoFile() {
        VitimageUtils.writeStringTabInCsv2(tabModifs, new File(dataDir, "InfoRSMLExpert.csv").getAbsolutePath().replace("\\", "/"));
    }

    public void startNewExpertize() {
//        this.stackPath = new File(dataDir, "22_registered_stack.tif").getAbsolutePath().replace("\\", "/");
//        this.rsmlPath = new File(dataDir, "61_graph.rsml").getAbsolutePath().replace("\\", "/");
        this.stackPath = new File(dataDir, "22_registered_stack.tif").getAbsolutePath().replace("\\", "/"); // Consider spaces " "
        this.rsmlPath = new File(dataDir, "61_graph.rsml").getAbsolutePath().replace("\\", "/");
        try {
            FileUtils.copyFile(new File(dataDir, "61_graph.rsml"), new File(dataDir, "61_graph_copy_before_expertize.rsml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        IJ.showMessage("Starting a new expertize of the box " + dataDir + "\n. Using 22_registered_stack.tif as image and 61_graph.rsml as arch. model to edit");
        IJ.showMessage("The rsml will be modified by expertize. But a copy of this have been made in case of, in 61_graph_copy_before_expertize.rsml");

        nModifs = 0;
        tabModifs = new String[500][nMaxModifs];
        for (String[] tabModif : tabModifs) Arrays.fill(tabModif, "");
        tabModifs[0][0] = this.stackPath;
        tabModifs[0][1] = this.rsmlPath;
        tabModifs[0][2] = this.version;
        writeInfoFile();
    }

    /* Helpers of the Gui ************************************************************************************/

    /**
     * This method initializes the Graphical User Interface (GUI) for the plugin.
     * It sets up the buttons and the frame for the log area.
     * It also logs the start of the Rsml Expert interface and sets the initial state of the buttons.
     * Additionally, it sets the tool in ImageJ to the hand tool and adds a key event dispatcher to handle key presses.
     */
    public void startGui() {
        // Set up the buttons and the button panel
        setupButtonsAndButtonPanel();

        // Set up the frame and the log area
        setupFrameAndLogArea();

        // Log the start of the Rsml Expert interface
        this.addLog("Starting Rsml Expert interface", 0);

        // Enable all buttons
        enable(all);

        // Disable the OK button
        disable(OK);

        // If there are no modifications, disable the UNDO button
        if (nModifs < 1) disable(UNDO);

        // Set the tool in ImageJ to the hand tool
        IJ.setTool("hand");

        // Add a key event dispatcher to handle key presses
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(
                new KeyEventDispatcher() {
                    public boolean dispatchKeyEvent(KeyEvent e) {
                        // If the key event is a key press, handle the key press
                        if (e.getID() == KeyEvent.KEY_PRESSED) {
                            handleKeyPress(e);
                        }
                        return false;
                    }
                });
    }

    public void setupFrameAndLogArea() {
        /*
         * The screen width.
         */
        int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width;
        if (screenWidth > 1920) screenWidth /= 2;
        frame = new JFrame();
        JPanel consolePanel = new JPanel();
        consolePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        consolePanel.setLayout(new GridLayout(1, 1, 0, 0));
        logArea.setSize(300, 80);
        logArea.setBackground(new Color(10, 10, 10));
        logArea.setForeground(new Color(245, 255, 245));
        logArea.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
        JScrollPane jscroll = new JScrollPane(logArea);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setEditable(false);


        frame = new JFrame();
        panelGlobal = new JPanel();
        frame.setSize(600, 680);
        panelGlobal.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panelGlobal.setLayout(new BoxLayout(panelGlobal, BoxLayout.Y_AXIS));
        panelGlobal.add(new JSeparator());
        panelGlobal.add(jscroll);
        panelGlobal.add(new JSeparator());
        panelGlobal.add(buttonsPanel);
        panelGlobal.add(new JSeparator());
        frame.add(panelGlobal);
        frame.setTitle("RSML Expert");
        frame.pack();
        frame.setSize(600, 900);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                IJ.showMessage("See you next time !");
                frame.setVisible(false);
                closeAllViews();
                if (isDevModeActivated) System.exit(0);
            }
        });
        frame.setVisible(true);
        frame.repaint();
        VitimageUtils.adjustFrameOnScreen(frame, 2, 0);
        logArea.setVisible(true);
        logArea.repaint();

    }

    /**
     * Setup buttons.
     */
    public void setupButtonsAndButtonPanel() {
        buttonsPanel = new JPanel();
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        buttonsPanel.setLayout(new GridLayout(16, 1, 10, 10));

        buttonUndo.addActionListener(this);
        buttonUndo.setToolTipText("<html><p width=\"500\">" + "Undo last action" + "</p></html>");
        buttonOk.addActionListener(this);
        buttonOk.setToolTipText("<html><p width=\"500\">" + "Click here to validate current points" + "</p></html>");
        buttonMove.addActionListener(this);
        buttonMove.setToolTipText("<html><p width=\"500\">" + "Change the position of a point" + "</p></html>");
        buttonRemove.addActionListener(this);
        buttonRemove.setToolTipText("<html><p width=\"500\">" + "Remove a point" + "</p></html>");
        buttonRefine.addActionListener(this);
        buttonRefine.setToolTipText("<html><p width=\"500\">" + "Add a new point" + "</p></html>");
        buttonSwitch.addActionListener(this);
        buttonSwitch.setToolTipText("<html><p width=\"500\">" + "Switch two crossing branches" + "</p></html>");
        buttonExtend.addActionListener(this);
        buttonExtend.setToolTipText("<html><p width=\"500\">" + "Extend an existing branch" + "</p></html>");
        buttonBackExtend.addActionListener(this);
        buttonExtend.setToolTipText("<html><p width=\"500\">" + "Backwards extension of an existing branch" + "</p></html>");
        buttonCreateLateral.addActionListener(this);
        buttonCreateLateral.setToolTipText("<html><p width=\"500\">" + "Create a new branch" + "</p></html>");
        buttonInfo.addActionListener(this);
        buttonInfo.setToolTipText("<html><p width=\"500\">" + "Inform about a node and its root" + "</p></html>");
        buttonChange.addActionListener(this);
        buttonChange.setToolTipText("<html><p width=\"500\">" + "Change time of a node" + "</p></html>");
        buttonResample.addActionListener(this);
        buttonResample.setToolTipText("<html><p width=\"500\">" + "Resample with target timestep" + "</p></html>");
        buttonSave.addActionListener(this);
        buttonSave.setToolTipText("<html><p width=\"500\">" + "Save the current model" + "</p></html>");
        buttonCreatePrimary.addActionListener(this);
        buttonCreatePrimary.setToolTipText("<html><p width=\"500\">" + "Create a primary root" + "</p></html>");
        // buttonFit.addActionListener(this);
        buttonFit.setToolTipText("<html><p width=\"500\">" + "Not available yet" + "</p></html>");
        buttonExit.addActionListener(this);
        buttonExit.setToolTipText("<html><p width=\"500\">" + "Exit the plugin" + "</p></html>");

        buttonsPanel.add(createLabel("----Points modification-------"));
        buttonsPanel.add(new JLabel());
        buttonsPanel.add(buttonOk);
        buttonsPanel.add(buttonUndo);
        buttonsPanel.add(buttonMove);
        buttonsPanel.add(buttonRemove);
        buttonsPanel.add(buttonRefine);
        buttonsPanel.add(buttonChange);

        buttonsPanel.add(createLabel("----Root modification---------"));
        buttonsPanel.add(new JLabel());
        buttonsPanel.add(buttonCreatePrimary);
        buttonsPanel.add(buttonCreateLateral);
        buttonsPanel.add(buttonExtend);
        buttonsPanel.add(buttonBackExtend);
        buttonsPanel.add(buttonFit);
        buttonsPanel.add(new JLabel());

        buttonsPanel.add(createLabel("----Multi organ modif---------"));
        buttonsPanel.add(new JLabel());
        buttonsPanel.add(buttonResample);
        buttonsPanel.add(buttonSwitch);

        buttonsPanel.add(createLabel("----Inspect and save---------"));
        buttonsPanel.add(new JLabel());
        buttonsPanel.add(buttonInfo);
        buttonsPanel.add(buttonSave);
        buttonsPanel.add(new JLabel());
        buttonsPanel.add(buttonExit);
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        return label;
    }

    /**
     * Setup image and rsml.
     * <p>
     * This method sets up the image and RSML (Root System Markup Language) model for the plugin.
     * It loads the registered stack image and the RSML model from the specified paths.
     * It then applies any modifications that have been made to the model.
     * The method also resizes the stack and projects the RSML model onto the current image.
     * Finally, it logs the steps/hours and mean timestep information.
     */
    public void setupImageAndRsml() {
        // Load the registered stack image from the specified path
        registeredStack = IJ.openImage(new File(stackPath).getAbsolutePath());

        // Initialize the FSR (Flying Software Renderer)
        //FSR sr = new FSR();
        //sr.initialize();
        IJ.run(registeredStack, "Enhance Contrast", "saturated=0.35");

        // Load the RSML model from the specified path
        currentModel = rootModelReadFromRsml(rsmlPath); // TODO Check reproducibility

        // Clean the RSML model and resample the flying roots
        System.out.println(currentModel.cleanWildRsml());
        System.out.println(currentModel.resampleFlyingRoots());
        //TODO : certification


        // Apply any modifications that have been made to the model
        System.out.println("Loading operations");
        for (int i = 0; i < tabModifs.length; i++) {
            String[] modif = tabModifs[i];
            System.out.println(i + " : " + modif[0]);
            if (modif[0].isEmpty()) break;
            // Skip first line
            System.out.println("Loading operations");
            if (tabModifs[0][0].equals(modif[0])) continue;

            readLineAndExecuteActionOnModel(modif, currentModel);
            nModifs++;
        }

        // Resize the stack
        tabReg = VitimageUtils.stackToSlices(registeredStack);
        imgInitSize = tabReg[0].duplicate();
        Nt = tabReg.length;
        tabRes = new ImagePlus[Nt];

        if (toResize) {
            // Determine the number of available processors
            int numProcessors = Runtime.getRuntime().availableProcessors() / 2;

            // Create an ExecutorService with a fixed thread pool
            ExecutorService executorService = Executors.newFixedThreadPool(numProcessors);

            // Submit tasks to the ExecutorService
            for (int i = 0; i < tabReg.length; i++) {
                final int index = i;
                executorService.submit(() ->
                        tabReg[index] = VitimageUtils.resize(tabReg[index], tabReg[index].getWidth() * zoomFactor,
                                tabReg[index].getHeight() * zoomFactor, 1));
            }

            // Shut down the ExecutorService
            executorService.shutdown();

            try {
                // Wait for all tasks to complete
                executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                // Handle interruption
                e.printStackTrace();
            }
            toResize = false;
        }

        // Project the RSML model onto the current image
        currentImage = projectRsmlOnImage(currentModel);
        currentImage.show();
        // Log the steps/hours and mean timestep information
        double[] tabHours = currentModel.hoursCorrespondingToTimePoints;
        StringBuilder stepsAndHours = new StringBuilder("Steps/hours : ");
        for (int i = 0; i < tabHours.length; i++) {
            stepsAndHours.append(" | ").append(i).append(" -> ").append(VitimageUtils.dou(tabHours[i]));
        }
        String meanTimestep = "Mean timestep = " + VitimageUtils.dou(tabHours[tabHours.length - 1] / (tabHours.length - 1));
        String logInfo = stepsAndHours + "\n" + meanTimestep;
        IJ.log(logInfo);
        addLog(stepsAndHours.toString(), -1);
        addLog(meanTimestep, -1);
    }

    /**
     * Handle key press.
     *
     * @param e the e
     */
    /* Callbacks  ********************************************************************************************/
    public void handleKeyPress(KeyEvent e) {
        final ExecutorService exec = Executors.newFixedThreadPool(1);
        exec.submit(new Runnable() {
            public void run() {
                if (e.getKeyChar() == 'q' && buttonOk.isEnabled()) {
                    disable(OK);
                    pointStart();
                    actionOkClicked();
                }
            }
        });
    }

    /**
     * Action performed.
     * This method is an event handler for various buttons in the application.
     * It checks which button was clicked and calls the corresponding method to handle the action.
     * Each action is run in a separate thread using an ExecutorService.
     *
     * @param e the ActionEvent object which contains information about the event
     */
    public void actionPerformed(ActionEvent e) {
        System.out.println("Got an event : " + e);

        final ExecutorService exec = Executors.newFixedThreadPool(1);
        exec.submit(new Runnable() {
            public void run() {
                if (e.getSource() == buttonOk && buttonOk.isEnabled()) {
                    disable(OK);
                    pointStart();
                    actionOkClicked();
                    return;
                }
                if (e.getSource() == buttonUndo && buttonUndo.isEnabled()) {
                    disable(UNDO);
                    pointStart();
                    actionUndo();
                    return;
                }
                if (e.getSource() == buttonMove && buttonMove.isEnabled()) {
                    disable(MOVE);
                    pointStart();
                    actionMovePoint();
                    return;
                }
                if (e.getSource() == buttonRemove && buttonRemove.isEnabled()) {
                    disable(REMOVE);
                    pointStart();
                    actionRemovePoint();
                    return;
                }
                if (e.getSource() == buttonRefine && buttonRefine.isEnabled()) {
                    disable(ADD);
                    pointStart();
                    actionRefineSegment();
                    return;
                }
                if (e.getSource() == buttonSwitch && buttonSwitch.isEnabled()) {
                    disable(SWITCH);
                    pointStart();
                    actionSwitchFalseCross();
                    return;
                }
                if (e.getSource() == buttonCreatePrimary && buttonCreatePrimary.isEnabled()) {
                    disable(CREATE);
                    pointStart();
                    actionCreatePrimary();
                    return;
                }
                if (e.getSource() == buttonCreateLateral && buttonCreateLateral.isEnabled()) {
                    disable(CREATE);
                    pointStart();
                    actionCreateBranch();
                    return;
                }
                if (e.getSource() == buttonExtend && buttonExtend.isEnabled()) {
                    disable(EXTEND);
                    pointStart();
                    actionExtendBranch();
                    return;
                }
                if (e.getSource() == buttonBackExtend && buttonBackExtend.isEnabled()) {
                    disable(BACKEXTEND);
                    pointStart();
                    actionBackExtendBranch();
                    return;
                }
                if (e.getSource() == buttonInfo && buttonInfo.isEnabled()) {
                    disable(INFO);
                    pointStart();
                    actionInfo();
                    return;
                }
                if (e.getSource() == buttonChange && buttonChange.isEnabled()) {
                    disable(CHANGE);
                    pointStart();
                    actionChangeTime();
                    return;
                }
                if (e.getSource() == buttonFit && buttonFit.isEnabled()) {
                    disable(RESAMPLE);
                    actionFitLastAction();
                    return;
                }
                if (e.getSource() == buttonSave && buttonSave.isEnabled()) {
                    disable(SAVE);
                    actionSave();
                    return;
                }
                if (e.getSource() == buttonResample && buttonResample.isEnabled()) {
                    disable(RESAMPLE);
                    actionResample();
                }
                if (e.getSource() == buttonExit && buttonExit.isEnabled()) {
                    IJ.showMessage("See you next time !");
                    frame.setVisible(false);
                    closeAllViews();
                    if (isDevModeActivated) System.exit(0);
                }

                // free the memory
                System.gc();
            }
        });
        exec.shutdown();
    }

    /**
     * Action undo.
     * This method is responsible for undoing the last action performed by the user.
     * It resets the current image and the ROI manager, and then reverts the changes made to the current model.
     * The method also logs the action, disables all buttons during the operation, and re-enables the UNDO button if
     * there are still actions to undo.
     */
    public void actionUndo() {
        // Delete the current Region of Interest in the image
        currentImage.deleteRoi();

        // Reset and close the ROI manager
        RoiManager.getRoiManager().reset();
        RoiManager.getRoiManager().close();

        // Log the start of the undo action
        addLog("Running action \"Undo !\" ...", -1);

        // Disable all buttons during the operation
        disable(all);

        Arrays.fill(tabModifs[nModifs], "");
        nModifs--;

        // Reload the model from the RSML file
        currentModel = rootModelReadFromRsml(rsmlPath); // TODO Check reproducibility

        // Reapply all modifications up to the current state
        int j = 1;
        while (!tabModifs[j][0].isEmpty()) {
            readLineAndExecuteActionOnModel(tabModifs[j], currentModel);
            j++;
        }

        // Clean and resample the model
        currentModel.cleanWildRsml();
        currentModel.resampleFlyingRoots();

        // Update the current image with the modified model
        try {
            VitimageUtils.actualizeData/*MultiThread*/(projectRsmlOnImage(currentModel), currentImage);
        }
        catch (Exception e) {
            VitimageUtils.actualizeData(projectRsmlOnImage(currentModel), currentImage);
        }

        // Log the successful completion of the undo action
        addLog("Ok.", 2);

        // Finish the action and update the UI
        finishActionAborted();

        // Write the current state of the modifications to the info file
        writeInfoFile();

        // If there are no more modifications to undo, disable the UNDO button
        if (nModifs < 1) disable(UNDO);
    }

    /**
     * Action ok clicked.
     */
    public void actionOkClicked() {
        okClicked = true;
    }

    /**
     * Action move point.
     */
    public void actionMovePoint() {
        boolean did = false;
        addLog("Running action \"Move a point\" ...", -1);
        addLog(" Click on the point to move, then the target destination.", 1);
        disable(all);
        Point3d[] tabPt = getAndAdaptCurrentPoints(waitPoints(2));
        String[] infos = null;
        if (tabPt != null) {
            infos = movePointInModel(tabPt, currentModel);
            did = true;
        }
        if (did && infos != null) finishActionThenGoOnStepSaveActionAndUpdateImage(infos);
        else finishActionAborted();
    }

    /**
     * Action remove point.
     */
    public void actionRemovePoint() {
        String[] infos = null;
        System.out.println("Rem0");
        addLog("Running action \"Remove point\" ...", -1);
        addLog(" Remove the point and all the children points of the root. Click on a point.", 1);
        System.out.println("Rem01");
        Point3d[] tabPt = getAndAdaptCurrentPoints(waitPoints(1));
        System.out.println("Rem02");

        boolean did = false;
        if (tabPt != null) {
            System.out.println("Rem2");
            infos = removePointInModel(tabPt, currentModel);
            if (infos != null) did = true;
        }
        System.out.println("Rem5");
        if (did) finishActionThenGoOnStepSaveActionAndUpdateImage(infos);
        else finishActionAborted();
        System.out.println("Rem7");

    }

    /**
     * Action add middle points.
     */
    public void actionRefineSegment() {
        String[] infos = null;
        boolean did = false;
        addLog("Running action \"Add point\" ...", -1);
        addLog(" Add point. Click on a line, then click on the middle point to add.", 1);
        enable(OK);
        waitOkClicked();
        Point3d[] tabPt = getAndAdaptCurrentPoints((PointRoi) currentImage.getRoi());
        if (tabPt != null) {
            infos = refineSegmentInModel(tabPt, currentModel);
        }
        if (infos != null) did = true;
        if (did) finishActionThenGoOnStepSaveActionAndUpdateImage(infos);
        else finishActionAborted();
    }

    /**
     * Action switch point.
     */
    public void actionSwitchFalseCross() {
        boolean did = false;
        addLog("Running action \"Switch cross\" ...", -1);
        addLog(" Resolve a X cross. Click on the first point of Root A before cross, and first point of Root B before cross.", 1);
        String[] infos = null;
        Point3d[] tabPt = getAndAdaptCurrentPoints(waitPoints(2));
        if (tabPt != null) {
            infos = switchFalseCrossInModel(tabPt, currentModel);
            if (infos != null) did = true;
        }
        if (did) finishActionThenGoOnStepSaveActionAndUpdateImage(infos);
        else finishActionAborted();
    }

    /**
     * Action extend branch.
     */
    public void actionExtendBranch() {
        boolean did = false;
        addLog("Running action \"Extend branch\" ...", -1);
        addLog("Click on the extremity of a branch, then draw the line for each following observations.", 1);
        enable(OK);
        String[] infos = null;
        waitOkClicked();
        Point3d[] tabPt = getAndAdaptCurrentPoints((PointRoi) currentImage.getRoi());
        if (tabPt != null) infos = extendBranchInModel(tabPt, currentModel);
        if (infos != null) did = true;
        if (did) finishActionThenGoOnStepSaveActionAndUpdateImage(infos);
        else finishActionAborted();
    }

    /**
     * Action backextend branch.
     */
    public void actionBackExtendBranch() {
        boolean did = false;
        addLog("Running action \"Back Extend branch\" ...", -1);
        addLog("Click on the first point of a branch, then draw the line for each previous observations.", 1);
        enable(OK);
        String[] infos = null;
        waitOkClicked();
        Point3d[] tabPt = getAndAdaptCurrentPoints((PointRoi) currentImage.getRoi());
        if (tabPt != null) infos = backExtendBranchInModel(tabPt, currentModel);
        if (infos != null) did = true;
        if (did) finishActionThenGoOnStepSaveActionAndUpdateImage(infos);
        else finishActionAborted();
    }

    /**
     * Action create branch.
     */
    public void actionCreateBranch() {
        boolean did = false;
        addLog("Running action \"Create branch\" ...", -1);
        addLog("Click on the start point of the branch at the emergence time, then draw the line for each following observations.", 1);
        enable(OK);
        String[] infos = null;
        waitOkClicked();
        Point3d[] tabPt = getAndAdaptCurrentPoints((PointRoi) currentImage.getRoi());
        if (tabPt != null) {
            infos = createBranchInModel(tabPt, currentModel);
        }
        if (infos != null) did = true;
        if (did) finishActionThenGoOnStepSaveActionAndUpdateImage(infos);
        else finishActionAborted();

    }

    public void actionCreatePrimary() {
        boolean did = false;
        addLog("Running action \"Create a primary root\" ...", -1);
        addLog("First click on the root peak at the start time, then continue clicking on different parts of the root until you reach the bottom.", 1);
        addLog("Then, draw the line for each following observations.", 1);
        enable(OK);
        String[] infos = null;
        waitOkClicked();
        Point3d[] tabPt = getAndAdaptCurrentPoints((PointRoi) currentImage.getRoi());
        if (tabPt != null) {
            infos = createPrimaryInModel(tabPt, currentModel);
        }
        if (infos != null) did = true;
        if (did) finishActionThenGoOnStepSaveActionAndUpdateImage(infos);
        else finishActionAborted();
    }

    public void actionFitLastAction() {
        boolean did = false;
        addLog("Running action \"Fitting curves\" ...", -1);
        String[] infos = null;
        //infos = fitLastActionRootsInModel(currentModel);
        if (infos != null) did = true;
        if (did) finishActionThenGoOnStepSaveActionAndUpdateImage(infos);
        else finishActionAborted();
    }

    /**
     * Action info.
     */
    public void actionInfo() {
        System.out.println("I1");
        //boolean did=false;
        addLog("Running action \"Inform about a node and a root\" ...", -1);
        addLog(" Click on the node you want to inspect.", 1);
        disable(all);
        System.out.println("I3");
        Point3d[] tabPt = getAndAdaptCurrentPoints(waitPoints(1));
        System.out.println("I4");
        //String[]infos=null;
        if (tabPt != null) {
            System.out.println("I5, len=" + tabPt.length);
            informAboutPointInModel(tabPt, currentModel);
            System.out.println("I7");
            //did=true;
        }
        System.out.println("I8");
        finishActionAborted();
        System.out.println("I9");

    }

    /**
     * Action change time.
     */
    public void actionChangeTime() {
        IJ.showMessage("Are you sure about what you are doing ? Save before, this is a dev feature. Next window could help you abort if you change your mind.");
        if (!VitiDialogs.getYesNoUI("Sure ?", "Sure")) {
            finishActionAborted();
        }
        System.out.println("I1");
        boolean did = false;
        addLog("Running action \"Change time of a node\" ...", -1);
        addLog(" Click on the node you want to change time.", 1);
        disable(all);
        System.out.println("I3");
        Point3d[] tabPt = getAndAdaptCurrentPoints(waitPoints(1));
        System.out.println("I4");
        String[] infos = null;
        if (tabPt != null) {
            System.out.println("I5, len=" + tabPt.length);
            infos = changeTimeInPointInModel(tabPt, currentModel);
            if (infos == null) did = false;
            System.out.println("I7");
        }
        System.out.println("I8");
        if (did) finishActionThenGoOnStepSaveActionAndUpdateImage(infos);
        else finishActionAborted();
    }

    /**
     * This method is responsible for resampling the Root System Markup Language (RSML) model.
     * It first disables all buttons in the GUI and retrieves the hours corresponding to each time point in the model.
     * It then logs the steps/hours and the mean timestep information.
     * The user is prompted to select the target timestep (in hours) and the output file name.
     * The method then calls the resampleModel method to perform the resampling operation.
     * If the resampling operation is successful, the method finishes the action and updates the image.
     * If the resampling operation is not successful, the method aborts the action.
     */
    public void actionResample() {//TODO : il n 'y a pas d'annuler

        // Initialize the did variable to false
        boolean did = false;

        // Disable all buttons in the GUI
        disable(all);

        // Retrieve the hours corresponding to each time point in the model
        double[] tabHours = currentModel.hoursCorrespondingToTimePoints;

        // Construct the steps/hours string
        StringBuilder s = new StringBuilder("Steps/hours : ");
        for (int i = 0; i < tabHours.length; i++) s.append(" | ").append(i).append(" -> ").append(VitimageUtils.dou(tabHours[i]));

        // Calculate the mean timestep
        String s2 = "Mean timestep = " + VitimageUtils.dou(tabHours[tabHours.length - 1] / (tabHours.length - 1));

        // Log the start of the resampling operation, the steps/hours, and the mean timestep
        addLog("Running action \"Resample RSML\" ...", -1);
        addLog(s.toString(), -1);
        addLog(s2, -1);

        // Prompt the user to select the target timestep (in hours)
        addLog(" Select the target timeStep (in hours).", 1);
        double timestep = VitiDialogs.getDoubleUI("Indicate the target timestep", "Timestep (in hours)", VitimageUtils.dou(tabHours[tabHours.length - 1] / (tabHours.length - 1)));

        // Prompt the user to select the output file name
        addLog(" Select the output file name.", 1);
        addLog(" Suggested : 61_graph_expertized_resample_" + timestep + "hours.rsml", 1);

        // Prompt the user to select the path to save the resampled RSML
        String path = VitiDialogs.saveImageUIPath("Path to your resampled rsml", "61_graph_expertized_resample_" + timestep + "hours.rsml");

        // Initialize the infos array to null
        String[] infos = null;

        // Perform the resampling operation
        infos = resampleModel(currentModel, timestep, path.replace("\\", "/"));

        // If the resampling operation is not successful, set did to false
        if (infos == null) did = false;

        // If the resampling operation is successful, finish the action and update the image
        if (did) finishActionThenGoOnStepSaveActionAndUpdateImage(infos);
            // If the resampling operation is not successful, abort the action
        else finishActionAborted();
    }

    /**
     * Action save.
     */
    public void actionSave() {
        System.out.println("I1");
        boolean did = false;
        addLog("Running action \"Save current RSML\" ...", -1);
        addLog(" Select the output file name.", 1);
        addLog(" Suggested : 61_graph_expertized.rsml", 1);
        disable(all);
        IJ.showMessage("Your expertized RSML will be written in \n" + this.dataDir + "/61_graph_expertized.rsml");
        String path = this.dataDir + "/61_graph_expertized.rsml";
        String[] infos = null;
        infos = saveExpertizedModel(currentModel, path.replace("\\", "/"));
        if (infos == null) did = false;
        if (did) finishActionThenGoOnStepSaveActionAndUpdateImage(infos);
        else finishActionAborted();
    }

    /**
     * Move point in model.
     *
     * @param tabPt the tab pt
     */
    /* Corresponding operations on the model *******************************************************************/
    public String[] movePointInModel(Point3d[] tabPt, RootModel rm) {
        String[] infos = formatInfos("MOVEPOINT", tabPt);
        Object[] obj = rm.getClosestNode(tabPt[0]);
        if (obj == null) {
            IJ.showMessage("You selected a weird node, that may not have appeared at the pointed time. Abort.");
            return null;
        }
        Node n = (Node) obj[0];
        Root r = (Root) obj[1];
        System.out.println("Moving :\n --> Node " + n + "\n --> Of root " + r);
        n.x = (float) tabPt[1].x;
        n.y = (float) tabPt[1].y;
        r.updateTiming();
        return infos;
    }

    /**
     * Removes the point in model.
     *
     * @param tabPt the tab pt
     * @return true, if successful
     */
    public String[] removePointInModel(Point3d[] tabPt, RootModel rm) {
        String[] infos = formatInfos("REMOVEPOINT", tabPt);

        Object[] obj = rm.getClosestNode(tabPt[0]);
        if (obj == null) {
            IJ.showMessage("You selected a weird node. Abort.");
            return null;
        }

        Node n1 = (Node) obj[0];
        Root r1 = (Root) obj[1];

        //Identify the first parent to stay
        while (n1.parent != null && !n1.parent.hasExactBirthTime()) {
            n1 = n1.parent;
        }


        System.out.println("Removing :\n --> Node " + n1 + "\n --> Of root " + r1);
        //Case where we remove a part of a primary
        System.out.println("Rem23");
        if (r1.childList != null && r1.childList.size() > 1) {
            for (Root rChi : r1.childList) {
                Node n = rChi.getNodeOfParentJustAfterMyAttachment();
                if (n1.isParentOrEqual(n)) {
                    System.out.println("But first, removing :\n --> Node " + n1 + "\n --> Of root " + r1);
                    removePointInModel(new Point3d[]{new Point3d(rChi.firstNode.x, rChi.firstNode.y, rChi.firstNode.birthTime)}, rm);
                }
            }
        }

        System.out.println("Rem24");
        //Case where we remove a tip
        if (n1 != r1.firstNode) {
            r1.lastNode = n1.parent;
            assert r1.lastNode != null;
            r1.lastNode.child = null;
            r1.updateTiming();
            return infos;
        } else {//Removing a full root
            System.out.println("Rem25");
            if (r1.getParent() != null) {        //Case where we remove the first point of a lateral root
                ArrayList<Root> childs = r1.getParent().childList;
                ArrayList<Root> newList = new ArrayList<Root>();
                for (Root r : childs) if (r != r1) newList.add(r);
                r1.getParent().childList = newList;
                System.out.println("Removing from childlist " + r1);
            }
            //General case : a lateral or a primary
            System.out.println("Rem26");
            ArrayList<Root> newList = new ArrayList<Root>();
            System.out.println("Removing from rootList " + r1);
            for (Root r : rm.rootList) if (r != r1) newList.add(r);
            rm.rootList = newList;
            System.out.println("Rem27");
            return infos;
        }
    }

    /**
     * Adds the middle points in model.
     *
     * @param tabPts the tab pts
     * @return true, if successful
     */
    public String[] refineSegmentInModel(Point3d[] tabPts, RootModel rm) {
        String[] infos = formatInfos("ADDMIDDLE", tabPts);
        if (tabPts.length < 2) {
            IJ.showMessage("This action needs you to click on 1) the line to change and 2) the point to add. Abort");
            return null;
        }

        Object[] obj = rm.getNearestRootSegment(tabPts[0], USER_PRECISION_ON_CLICK);
        if (obj[0] == null) {
            IJ.showMessage("Please click better, we have not found the corresponding segment");
            return null;
        }
        Node nParent = (Node) obj[0];
        Root rParent = (Root) obj[1];
        Node rChild = nParent.child;
        float rParentBirthTime = nParent.birthTime;
        float rChildBirthTime = rChild.birthTime;
        float rParentBirthTimeHours = nParent.birthTimeHours;
        float rChildBirthTimeHours = rChild.birthTimeHours;
        System.out.println("Adding nodes in segment :\n --> Node 1 " + nParent + "\n --> Node 2 " + rChild + "\n --> Of root " + rParent);

        for (int i = 1; i < tabPts.length; i++) {
            Node nPlus = new Node((float) tabPts[i].x, (float) tabPts[i].y, nParent, true);
            // nPlus.birthTime = 0.5f; previous bug
            // Birthtime = (time of parent) + (time of child - time of parent) * ((time of new point - time of parent) / (time of child - time of parent))
            nPlus.birthTime = (float) (rParentBirthTime + (rChildBirthTime - rParentBirthTime) * ((tabPts[i].z - rParentBirthTime) / (rChildBirthTime - rParentBirthTime)));
            nPlus.birthTimeHours = (float) (rParentBirthTimeHours + (rChildBirthTimeHours - rParentBirthTimeHours) * ((tabPts[i].z - rParentBirthTime) / (rChildBirthTime - rParentBirthTime)));
            nParent.child = nPlus;
            nPlus.parent = nParent;
            nParent = nPlus;
        }
        nParent.child = rChild;
        rChild.parent = nParent;
        rParent.updateTiming();
        return infos;
    }

    /**
     * Switch point in model.
     *
     * @param tabPt the tab pt
     * @return true, if successful
     */
    public String[] switchFalseCrossInModel(Point3d[] tabPt, RootModel rm) {
        String[] infos = formatInfos("SWITCHPOINT", tabPt);
        Object[] obj1 = rm.getClosestNode(tabPt[0]);
        Object[] obj2 = rm.getClosestNode(tabPt[1]);
        if (obj1 == null || obj2 == null) {
            IJ.showMessage("The point selected are not convenient. Abort.");
            return null;
        }

        Node n1 = (Node) obj1[0];
        Root r1 = (Root) obj1[1];
        Node n2 = (Node) obj2[0];
        Root r2 = (Root) obj2[1];

        boolean isFeasible = !(n1.parent.birthTime >= n2.birthTime);
        if (n2.parent.birthTime >= n1.birthTime) isFeasible = false;
        if (n1.child.birthTime <= n2.birthTime) isFeasible = false;
        if (n2.child.birthTime <= n1.birthTime) isFeasible = false;
        System.out.println("Trying to switch :\n --> Node " + n1 + "\n and node n2 " + n2);

        if (!isFeasible) {
            IJ.showMessage("This switch is not possible");
            return null;
        }
        Node par1 = n1.parent;
        Node chi1 = n1.child;
        n1.parent = n2.parent;
        n1.child = n2.child;
        n2.parent = par1;
        n2.child = chi1;
        r1.resampleFlyingPoints(rm.hoursCorrespondingToTimePoints);
        r1.updateTiming();
        r2.resampleFlyingPoints(rm.hoursCorrespondingToTimePoints);
        r2.updateTiming();
        return infos;
    }

    /**
     * Create a primary root in model.
     *
     * @param tabPt the tab of points
     * @param rm    the root model
     * @return infos on the action performed
     */
    public String[] createPrimaryInModel(Point3d[] tabPt, RootModel rm) {
        // Check if there are at least two points provided for the branch
        if (tabPt.length < 2) return null;

        for (int l = 0; l < tabPt.length - 1; l++) {
            if (tabPt[l] == null || tabPt[l + 1] == null) {
                IJ.showMessage("You gave a null point. Abort.");
                return null;
            }
            if ((tabPt[l + 1].z - tabPt[l].z) > 1) {
                IJ.showMessage("You gave points that does not follow in time, there is a gap. Abort.");
                return null;
            }

            if (tabPt[l].z > tabPt[l + 1].z) {
                IJ.showMessage("You gave points that are not in chronological order. Abort.");
                return null;
            }
        }

        // All points at first selected time except the last one are shifted down by 1 in time (only the tip remains at the initial time)
        Point3d[] tabPtArranged = new Point3d[tabPt.length];
        if (tabPt[1].z == tabPt[0].z && tabPt[0].z == 1) tabPtArranged[0] = new Point3d(tabPt[0].x, tabPt[0].y, 0);
        else tabPtArranged[0] = new Point3d(tabPt[0].x, tabPt[0].y, tabPt[0].z);
        int i = 1;
        while (tabPt[i].z == 1 && tabPt[i+1].z == 1 && i < tabPt.length - 2) {
            tabPtArranged[i] = new Point3d(tabPt[i].x, tabPt[i].y, tabPt[0].z - 1);
            i++;
        }

        for (int k = i; k < tabPt.length; k++) {
            tabPtArranged[k] = new Point3d(tabPt[k].x, tabPt[k].y, tabPt[k].z);
        }

        Point3d pt0 = tabPtArranged[0];
        Node n = new Node((float) pt0.x, (float) pt0.y, null, false);
        // if it is an extremity, we set the birth time to the exact time
        n.birthTime = (float) pt0.z;
        n.birthTimeHours = (float) rm.hoursCorrespondingToTimePoints[(int) pt0.z];
        Node nPar = n;

        // Iterate over the points to create new nodes and link them
        for (Point3d pt : tabPtArranged) {
            if (pt == pt0) continue; // skip the first point, already created
            //else if (pt == tabPtArranged[tabPtArranged.length - 1]) continue; // skip the last point, will be created in the next step
            Node nNew = new Node((float) pt.x, (float) pt.y, nPar, true);
            nNew.birthTime = (float) pt.z;
            nNew.birthTimeHours = (float) rm.hoursCorrespondingToTimePoints[(int) pt.z];
            nPar.child = nNew;
            nPar = nNew;
        }

        String rootId = "AnnotatedPrimary_" + (rm.rootList.size() + 1);
        Root rNew = new Root(null, rm, rootId, 1);
        rNew.firstNode = n;
        rNew.lastNode = nPar;
        rNew.updateTiming();
        rm.rootList.add(rNew);
        rm.increaseNbPlants(); // TODO determine if needed (monocotyledon or dicotyledon ?)

        return formatInfos("CREATEPRIMARY", tabPt);
    }

    /**
     * This method creates a new branch in the RootModel.
     * It first checks if there are at least two points provided for the branch.
     * Then it finds the closest node in the primary root to the first point.
     * It checks if the points are in the correct time order and if any time slices are missed.
     * If all checks pass, it creates a new Node for each point and links them together to form a branch.
     * The new branch is then added to the RootModel.
     *
     * @param tabPt an array of Point3d objects representing the points of the new branch
     * @param rm    the RootModel object to which the new branch will be added
     * @return an array of Strings containing information about the operation, or null if the operation was not
     * successful
     */
    public String[] createBranchInModel(Point3d[] tabPt, RootModel rm) {
        String[] infos = formatInfos("CREATEBRANCH", tabPt);
        if (tabPt.length < 2) return null;

        Object[] obj = rm.getClosestNodeInPrimary(tabPt[0]); // TODO : this assumes branches can only emerge from primary roots
        if (obj == null) {
            IJ.showMessage("The branch has not yet appeared. Abort.");
            return null;
        }

        Node n = (Node) obj[0];
        Root r = (Root) obj[1];
        System.out.println("Creating branch from :\n --> Node " + n + "\n --> Of root " + r);

        // Check if the points are in the correct time order and if any time slices are missed
        for (int l = 0; l < tabPt.length - 1; l++) {

            if (tabPt[l].z > tabPt[l + 1].z) {
                IJ.showMessage("You gave points that are not in chronological order. Abort.");
                return null;
            }
            if (tabPt[l] == null || tabPt[l + 1] == null) {
                IJ.showMessage("You gave a null point. Abort.");
                return null;
            }

            if ((tabPt[l + 1].z - tabPt[l].z) > 1) {
                IJ.showMessage("You gave points that does not follow in time, there is a gap. Abort.");
                return null;
            }
        }

        n = new Node((float) tabPt[0].x, (float) tabPt[0].y, null, false);
        n.birthTime = (float) tabPt[0].z;
        n.birthTimeHours = (float) rm.hoursCorrespondingToTimePoints[(int) tabPt[0].z];
        Node firstNode = n;

        Node nPar = n;

        // Iterate over the points to create new nodes and link them
        for (Point3d pt : tabPt) {
            if (pt == tabPt[0]) continue;
            Node nNew = new Node((float) pt.x, (float) pt.y, nPar, true);
            nNew.birthTime = (float) pt.z;
            nNew.birthTimeHours = (float) rm.hoursCorrespondingToTimePoints[(int) pt.z];
            nPar.child = nNew;
            nPar = nNew;
        }

        Root rNew = new Root(null, rm, "", r.getOrder() + 1);
        rNew.firstNode = firstNode;
        rNew.lastNode = nPar;
        rNew.updateTiming();
        r.attachChild(rNew);
        rNew.attachParent(r);
        rm.rootList.add(rNew);

        return infos;
    }

    /**
     * Extend branch in model.
     *
     * @param tabPt the array of 3D points
     * @param rm    the root model
     * @return array of strings containing formatted information, or null if unsuccessful
     */
    public String[] extendBranchInModel(Point3d[] tabPt, RootModel rm) {
        // Format and return initial information about the branch extension
        String[] infos = formatInfos("EXTENDBRANCH", tabPt);

        // Ensure there are at least 2 points to form a branch
        if (tabPt.length < 2) return null;

        // Get the closest node to the first point in the list
        Object[] obj = rm.getClosestNode(tabPt[0]);
        if (obj == null) {
            IJ.showMessage("The branch has not yet appeared. Abort.");
            return null;
        }

        // Extract the node and root from the closest node result
        Node n = (Node) obj[0];
        Root r = (Root) obj[1];

        System.out.println("Extending branch from:\n --> Node " + n + "\n --> Of root " + r);

        // Check if the node is the last node of the root
        if (n != r.lastNode) {
            IJ.showMessage("Please select the last point of the branch you want to extend. Abort.");
            return null;
        }

        // Validate the birth time of the node
        if (n.birthTime != (tabPt[0].z < 1 ? 0 : tabPt[0].z)) {
            IJ.showMessage("Please select the first point of the branch you want to extend at the right time. Abort.");
            return null;
        }

        // Validate the chronological order and continuity of the points
        for (int l = 0; l < tabPt.length - 1; l++) {
            if (tabPt[l].z > tabPt[l + 1].z) {
                IJ.showMessage("You gave points that are not in chronological order. Abort.");
                return null;
            }
            if (tabPt[l] == null || tabPt[l + 1] == null) {
                IJ.showMessage("You gave a null point. Abort.");
                return null;
            }
            if ((tabPt[l + 1].z - tabPt[l].z) > 1) {
                IJ.showMessage("You gave points that do not follow in time, there is a gap. Abort.");
                return null;
            }
        }

        // Save the parent of the original node
        Node nPar = n.parent; // assuming branch has at least 2 points

        // Create a new first node for the branch
        Node nFirst = new Node(n.x, n.y, nPar, true);
        nFirst.birthTime = n.birthTime;
        nFirst.birthTimeHours = n.birthTimeHours;
        nPar.child = nFirst;
        Node nn = null;

        // Iterate over the points to create new nodes and link them
        for (int i = 1; i < tabPt.length; i++) {
            nn = new Node((float) tabPt[i].x, (float) tabPt[i].y, nFirst, true);
            nn.birthTime = (float) tabPt[i].z;
            nn.birthTimeHours = (float) rm.hoursCorrespondingToTimePoints[(int) tabPt[i].z];
            nFirst.child = nn;
            nFirst = nn;
        }

        // Update the last node of the root and update the timing
        r.lastNode = nFirst;
        r.updateTimingModifiedForDebuggingRSMLExpert();

        return infos;
    }

    /**
     * Extend branch in model.
     *
     * @param tabPt the array of 3D points
     * @param rm    the root model
     * @return array of strings containing formatted information, or null if unsuccessful
     */
    public String[] backExtendBranchInModel(Point3d[] tabPt, RootModel rm) {
        // Format and return initial information about the branch extension
        String[] infos = formatInfos("BACKEXTENDBRANCH", tabPt);

        // Ensure there are at least 2 points to form a branch
        if (tabPt.length < 2) return null;

        // Print the original points
        for (Point3d pt : tabPt) System.out.println(pt);

        // Reverse the order of the points
        for (int i = 0; i < tabPt.length / 2; i++) {
            Point3d temp = tabPt[i];
            tabPt[i] = tabPt[tabPt.length - 1 - i];
            tabPt[tabPt.length - 1 - i] = temp;
        }

        // Print the reversed points
        for (Point3d pt : tabPt) System.out.println(pt);

        // Get the closest node to the last point in the reversed list
        Object[] obj = rm.getClosestNode(tabPt[tabPt.length - 1]);

        // If no closest node is found, abort the operation
        if (obj == null) {
            IJ.showMessage("The branch has not yet appeared. Abort.");
            return null;
        }

        // Extract the node and root from the closest node result
        Node n = (Node) obj[0];
        Root r = (Root) obj[1];

        System.out.println("Back-Extending branch from:\n --> Node " + n + "\n --> Of root " + r);

        // Check if the node is the first node of the root
        if (n != r.firstNode) {
            IJ.showMessage("The node clicked seems not to be the first node of the corresponding root\n" +
                    "Please select the first point of the branch you want to extend. Abort.");
            return null;
        }

        // Validate the birth time of the node
        if ((n.birthTime == 0 ? 1 : n.birthTime) != tabPt[tabPt.length - 1].z) {
            IJ.showMessage("Please select the first point of the branch you want to extend at the right time. Abort.");
            return null;
        }

        // Validate the chronological order and continuity of the points
        for (int l = 0; l < tabPt.length - 1; l++) {
            if (tabPt[l].z > tabPt[l + 1].z) {
                IJ.showMessage("You gave points that are not in chronological order. Abort.");
                return null;
            }
            if (tabPt[l] == null || tabPt[l + 1] == null) {
                IJ.showMessage("You gave a null point. Abort.");
                return null;
            }
            if ((tabPt[l + 1].z - tabPt[l].z) > 1) {
                IJ.showMessage("You gave points that do not follow in time, there is a gap. Abort.");
                return null;
            }
        }

        // Save the position and child of the original first node of the root
        Point3d oldFirst = new Point3d(r.firstNode.x, r.firstNode.y, r.firstNode.birthTime);
        Node childSon = r.firstNode.child;  // assuming root has at least 2 points

        // Create a new first node for the root
        Node nFirst = new Node((float) tabPt[0].x, (float) tabPt[0].y, null, true);
        nFirst.birthTime = (float) tabPt[0].z;
        nFirst.birthTimeHours = (float) rm.hoursCorrespondingToTimePoints[(int) tabPt[0].z];
        r.firstNode = nFirst;
        Node newFirst = nFirst;
        Node nn = null;

        // Iterate over the points to create new nodes and link them
        for (int i = 1; i < tabPt.length - 1; i++) {
            nn = new Node((float) tabPt[i].x, (float) tabPt[i].y, nFirst, true);
            nn.birthTime = (float) tabPt[i].z;
            nn.birthTimeHours = (float) rm.hoursCorrespondingToTimePoints[(int) tabPt[i].z];
            nFirst.child = nn;
            nFirst = nn;
        }

        if (nn == null) nn = newFirst;

        // Replace the original first node with a new one and reconnect the root structure
        Node remake = new Node((float) oldFirst.x, (float) oldFirst.y, nn, true);
        remake.birthTime = (float) oldFirst.z;
        remake.birthTimeHours = (float) rm.hoursCorrespondingToTimePoints[(int) oldFirst.z];
        nn.child = remake;
        remake.child = childSon;
        r.ComputeParentNode();

        // Update the root timing and clear the points' map
        r.updateTiming();
        return infos;
    }

    /**
     * Inform about point in model.
     *
     * @param tabPt the tab pt
     */
    public void informAboutPointInModel(Point3d[] tabPt, RootModel rm) {
        Object[] obj = rm.getClosestNode(tabPt[0]);
        if (obj == null) {
            IJ.showMessage("Please don't. Abort.");
            return;
        }

        Node n = (Node) obj[0];
        Root r = (Root) obj[1];
        IJ.showMessage("Informations at coordinates " + tabPt[0] + " :\n --> Node " + n + "\n --> Of root " + r);
    }

    /**
     * Change time in point in model.
     *
     * @param tabPt the tab pt
     * @return true, if successful
     */
    public String[] changeTimeInPointInModel(Point3d[] tabPt, RootModel rm) {
        String[] infos = formatInfos("CHANGETIME", tabPt);
        Object[] obj = rm.getClosestNode(tabPt[0]);
        if (obj == null) return null;
        Node n = (Node) obj[0];
        Root r = (Root) obj[1];
        if (n == null) return null;
        double tt = VitiDialogs.getDoubleUI("New time", "time", n.birthTime);
        n.birthTime = (float) tt;
        r.resampleFlyingPoints(rm.hoursCorrespondingToTimePoints);
        r.updateTiming();
        return infos;
    }

    /**
     * Save expertized model
     *
     * @param rm   the RootModl
     * @param path the path to save the final exported model, as a rsml file
     * @return true, if successful
     */
    public String[] saveExpertizedModel(RootModel rm, String path) {
        String[] infos = formatInfos("SAVE_" + new File(path).getName(), new Point3d[]{new Point3d(0, 0, 0)});
        rm.writeRSML3D(new File(path).getAbsolutePath().replace("\\", "/"), "", true, false);
        return infos;
    }

    /**
     * Change time in point in model.
     * <p>
     * param tabPt the tab pt
     *
     * @return true, if successful
     * <p>
     * OLD : public String[] resampleModel(RootModel rm, double timestep, String path) {
     * IJ.log("Called action resampleModel");
     * String[] infos = formatInfos("RESAMPLE_" + new File(path).getName(), new Point3d[]{new Point3d(timestep, 0, 0)});
     * RootModel rm2 = RootModel.RootModelWildReadFromRsml(rsmlPath);
     * //ystem.out.println(rm2.cleanWildRsml()) ;
     * //System.out.println(rm2.resampleFlyingRoots());
     * int j = 1;
     * while (!tabModifs[j][0].equals("")) {
     * readLineAndExecuteActionOnModel(tabModifs[j], rm2);
     * j++;
     * }
     * IJ.log("Starting callback");
     * RootModel romod = resampleRootModelToTargetTimestep(rm2, timestep);
     * romod.writeRSML3D(new File(path).getAbsolutePath().replace("\\", "/"), "", true, false);
     * <p>
     * return infos;
     * }
     */
    public String[] resampleModel(RootModel rm, double timestep, String path) {
        String[] infos = formatInfos("RESAMPLE_" + new File(path).getName(), new Point3d[]{new Point3d(timestep, 0,
                0)});
        RootModel rm2 = rootModelReadFromRsml(rsmlPath); // TODO Check reproducibility

        IntStream.range(1, tabModifs.length)
                .parallel()
                .filter(j -> !tabModifs[j][0].isEmpty())
                .forEach(j -> readLineAndExecuteActionOnModel(tabModifs[j], rm2));

        RootModel romod = resampleRootModelToTargetTimestep(rm2, timestep);
        romod.writeRSML3D(path.replace("\\", "/"), "", true, false);

        return infos;
    }

    public RootModel resampleRootModelToTargetTimestep(RootModel rm, double timestep) {
        //Preparing variables
        double[] tabHours = Arrays.copyOf(rm.hoursCorrespondingToTimePoints, rm.hoursCorrespondingToTimePoints.length);
        int NimgInit = this.registeredStack.getStackSize();
        double hourMax = tabHours[tabHours.length - 1];
        int N = (int) Math.floor(hourMax / timestep) + 1;
        double[] hours = new double[N];
        int[] correspondingImage = new int[N];

        //Identifying for each timepoint the corresponding image in the original stack, and the actual time since experiment start
        for (int i = 0; i < N; i++) {
            hours[i] = i * timestep;
            int index = NimgInit - 1;
            for (int j = NimgInit; j > 0; j--) {
                if (tabHours[j] > hours[i]) index = j - 1;
            }
            correspondingImage[i] = index;
            IJ.log("Timestep " + i + " = " + hours[i] + " identified keyimage=" + index);
        }

        //Change the timebasis and use flyingRootsMethod to create new nodes along with the timepoints
        rm.changeTimeBasis(timestep, N);
        rm.resampleFlyingRoots();

        //Remove all the timepoints not falling onto actual timepoints
        rm.removeInterpolatedNodes();

        Timer t = new Timer();
        ImagePlus[] tabImg = new ImagePlus[N];
        for (int i = 0; i < N; i++) {
            t.print("Projecting " + i + " / " + N);
            ImagePlus imgRSML = rm.createGrayScaleImageWithHours(imgInitSize, zoomFactor, false, hours[i], true, new boolean[]{true, true, true, false, true}, new double[]{2, 2});
            imgRSML.setDisplayRange(-timestep, hourMax);
            tabImg[i] = RGBStackMerge.mergeChannels(new ImagePlus[]{tabReg[correspondingImage[i]], imgRSML}, true);
            IJ.run(tabImg[i], "RGB Color", "");
        }
        t.print("Projecting resampled root model took : ");
        ImagePlus res = VitimageUtils.slicesToStack(tabImg);
        res.setTitle("Model_resampled_timestep_" + timestep + "hours");
        res.show();
        return rm;
    }

    public String[] formatInfos(String action, Point3d[] tabPt) {
        int nbPoints = tabPt.length;
        String[] out = new String[2 + 4 * nbPoints];
        out[0] = action;
        out[1] = "" + nbPoints;
        for (int i = 0; i < nbPoints; i++) {
            out[2 + i * 4] = ("Pt_" + i);
            out[2 + i * 4 + 1] = ("" + VitimageUtils.dou(tabPt[i].x));
            out[2 + i * 4 + 2] = ("" + VitimageUtils.dou(tabPt[i].y));
            out[2 + i * 4 + 3] = ("" + VitimageUtils.dou(tabPt[i].z));
        }
        return out;
    }

    public void readLineAndExecuteActionOnModel(String[] line, RootModel rm) {
        int nPoints = Integer.parseInt(line[1]);
        Point3d[] tabPt = new Point3d[nPoints];
        for (int i = 0; i < nPoints; i++) {
            tabPt[i] = new Point3d(Double.parseDouble(line[2 + i * 4 + 1]), Double.parseDouble(line[2 + i * 4 + 2]), Double.parseDouble(line[2 + i * 4 + 3]));
        }
        String action = line[0];

        switch (action) {
            case "MOVEPOINT":
                movePointInModel(tabPt, rm);
                break; // TODO
            case "REMOVEPOINT":
                removePointInModel(tabPt, rm);
                break;// TODO
            case "ADDMIDDLE":
                refineSegmentInModel(tabPt, rm);
                break;// TODO
            case "SWITCHPOINT":
                switchFalseCrossInModel(tabPt, rm);
                break;// TODO
            case "CREATEPRIMARY":
                createPrimaryInModel(tabPt, rm);
                break;// TODO
            case "CREATEBRANCH":
                createBranchInModel(tabPt, rm);
                break;// TODO
            case "EXTENDBRANCH":
                extendBranchInModel(tabPt, rm);
                break;// TODO
            case "BACKEXTENDBRANCH":
                backExtendBranchInModel(tabPt, rm);
                break;// TODO
            case "CHANGETIME":
                changeTimeInPointInModel(tabPt, rm);
                break;// TODO
            default:
                // Handle the case where no matching action is found
                break;// TODO
        }
    }

    /**
     * Finish action aborted.
     */
    /* Helpers for starting and finishing actions *******************************************************************/
    public void finishActionAborted() {
        IJ.setTool("hand");
        addLog(" action aborted.", 2);
        enable(all);
        disable(OK);
    }

    /**
     * This method is used to finalize an action, update the image and save the action's information.
     * It is typically called after an action has been successfully completed.
     *
     * @param infos An array of Strings containing information about the action that was performed.
     *              This information is copied into the tabModifs array for future reference.
     */
    public void finishActionThenGoOnStepSaveActionAndUpdateImage(String[] infos) {
        // Set the current tool in ImageJ to the "hand" tool
        IJ.setTool("hand");

        // Log that the action was successfully completed
        addLog(" action ok.", 2);

        // Log that the image is being updated
        addLog("Updating image...", 0);

        // Increment the modification counter
        nModifs++;

        // Copy the action information into the tabModifs array
        System.arraycopy(infos, 0, tabModifs[nModifs], 0, infos.length);

        // Update the data in the current image based on the current model
        try {
            VitimageUtils.actualizeData(projectRsmlOnImage(currentModel), currentImage);
        }
        catch (Exception e) {
            VitimageUtils.actualizeData(projectRsmlOnImage(currentModel), currentImage);
        }
        // Log that the image update was successful
        addLog("Ok.", 2);

        // Enable all buttons
        enable(all);

        // Disable the OK button
        disable(OK);

        // Write the information about the action to a file
        writeInfoFile();
    }

    /**
     * This method is used to initiate the point selection process in the graphical interface.
     * It disables all buttons, resets the Region of Interest (ROI) Manager and sets the current tool to "multipoint".
     * The "multipoint" tool allows the user to select multiple points in the image.
     */
    public void pointStart() {
        // Disable all buttons in the graphical interface
        disable(all);

        // Get the instance of the ROI Manager
        RoiManager rm = RoiManager.getRoiManager();

        // Reset the ROI Manager to clear any existing selections
        rm.reset();

        // Set the current tool to "multipoint" to allow the user to select multiple points in the image
        IJ.setTool("multipoint");
    }

    /**
     * Gets the and adapt current points.
     *
     * @param pr the pr
     * @return the and adapt current points
     */
    public Point3d[] getAndAdaptCurrentPoints(PointRoi pr) {
        if (pr == null) {
            currentImage.deleteRoi();
            RoiManager.getRoiManager().reset();
            RoiManager.getRoiManager().close();
            return null;
        }
        Point[] tab2D = pr.getContainedPoints();
        Point3d[] tabPt = new Point3d[tab2D.length];
        for (int i = 0; i < tabPt.length; i++) {
            tabPt[i] = new Point3d((double) tab2D[i].x / zoomFactor, (double) tab2D[i].y / zoomFactor, pr.getPointPosition(i));
            System.out.println("Processed point " + i + ": " + tabPt[i]);
        }
        currentImage.deleteRoi();
        RoiManager.getRoiManager().reset();
        RoiManager.getRoiManager().close();
        return tabPt;
    }

    /**
     * This method projects the Root System Markup Language (RSML) model onto an image.
     * It creates a grayscale image for each time point in the model, then merges this with the registered stack image.
     * The resulting images are combined into a stack and returned.
     *
     * @param rm the RootModel object which contains the RSML data
     * @return an ImagePlus object which is a stack of images with the RSML model projected onto them
     */
    public ImagePlus projectRsmlOnImage(RootModel rm) {
        // Start a timer to measure the execution time of this method
        Timer t = new Timer();

        // Create an array to store processed images
        ImagePlus[] processedImages = new ImagePlus[Nt];

        try {
            // Loop over each time point in the model
            IntStream.range(0, Nt).parallel().forEach(i -> {
                // Create a grayscale image of the RSML model at this time point
                ImagePlus imgRSML = rm.createGrayScaleImageWithTime(imgInitSize, zoomFactor, false, (i + 1), true,
                        new boolean[]{true, true, true, false, true}, new double[]{2, 2});

                // Set the display range of the image
                imgRSML.setDisplayRange(0, Nt + 3);

                // Merge the grayscale image with the registered stack image
                processedImages[i] = RGBStackMerge.mergeChannels(new ImagePlus[]{tabReg[i], imgRSML}, true);
                // Convert the image to RGB color
                IJ.run(processedImages[i], "RGB Color", "");
            });
        }
        catch (Exception e) {
            // Loop over each time point in the model
            IntStream.range(0, Nt).forEach(i -> {
                // Create a grayscale image of the RSML model at this time point
                ImagePlus imgRSML = rm.createGrayScaleImageWithTime(imgInitSize, zoomFactor, false, (i + 1), true,
                        new boolean[]{true, true, true, false, true}, new double[]{2, 2}); // TODO RGB

                // Set the display range of the image
                imgRSML.setDisplayRange(0, Nt + 3);

                // Merge the grayscale image with the registered stack image
                processedImages[i] = RGBStackMerge.mergeChannels(new ImagePlus[]{tabReg[i], imgRSML}, true);
                // Convert the image to RGB color
                IJ.run(processedImages[i], "RGB Color", "");
            });
        }

        // Print the execution time of this method
        t.print("Updating root model took : ");

        // Combine the images into a stack
        ImagePlus res = VitimageUtils.slicesToStack(processedImages);

        // Get the name of the box
        String chain = getBoxName();

        // Create a name for the image
        String nom =
                "Model_of_box_" + chain + "_at_step_" + String.format("%04d", nModifs);

        // Set the title of the image
        res.setTitle(nom);

        // Return the image
        return res;
    }

    /**
     * Wait ok clicked.
     */
    public void waitOkClicked() {
        while (!okClicked) {
            VitimageUtils.waitFor(100);
        }
        okClicked = false;
    }

    /**
     * Wait points.
     *
     * @param nbExpected the nb expected
     * @return the point roi
     */
    public PointRoi waitPoints(int nbExpected) {
        Roi r = null;
        PointRoi pr = null;
        while (count != nbExpected) {
            VitimageUtils.waitFor(100);
            r = currentImage.getRoi();
            if (r != null) {
                pr = ((PointRoi) r);
                count = pr.getContainedPoints().length;
            }
        }
        count = 0;
        return pr;
    }

    /**
     * Adds the log.
     *
     * @param t     the t
     * @param level the level
     */
    public void addLog(String t, int level) {
        if (level == -1) logArea.append("\n\n > " + t);
        if (level == 0) logArea.append("\n > " + t);
        if (level == 1) logArea.append("\n " + t);
        if (level == 2) logArea.append(" " + t);
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    /**
     * Key pressed.
     *
     * @param arg0 the arg 0
     */
    public void keyPressed(KeyEvent arg0) {
    }

    /**
     * Key released.
     *
     * @param arg0 the arg 0
     */
    public void keyReleased(KeyEvent arg0) {
    }

    /**
     * Key typed.
     *
     * @param arg0 the arg 0
     */
    public void keyTyped(KeyEvent arg0) {
    }

    /**
     * Close all views.
     */
    public void closeAllViews() {
        if (currentImage != null) currentImage.close();
        if (RoiManager.getInstance() != null) RoiManager.getInstance().close();
    }

    /**
     * Enable.
     *
     * @param but the but
     */
    public void enable(int but) {
        enable(new int[]{but});
    }

    /**
     * Disable.
     *
     * @param but the but
     */
    public void disable(int but) {
        disable(new int[]{but});
    }

    /**
     * Enable.
     *
     * @param tabBut the tab but
     */
    public void enable(int[] tabBut) {
        setState(tabBut, true);
    }

    /**
     * Disable.
     *
     * @param tabBut the tab but
     */
    public void disable(int[] tabBut) {
        setState(tabBut, false);
    }

    /**
     * Sets the state.
     *
     * @param tabBut the tab but
     * @param state  the state
     */
    public void setState(int[] tabBut, boolean state) {
        for (int but : tabBut) {
            switch (but) {
                case OK:
                    this.buttonOk.setEnabled(state);
                    break;
                case CREATE:
                    this.buttonCreateLateral.setEnabled(state);
                    this.buttonCreatePrimary.setEnabled(state);
                    break;
                case FIT:
                    this.buttonFit.setEnabled(state);
                    break;
                case EXTEND:
                    this.buttonExtend.setEnabled(state);
                    break;
                case BACKEXTEND:
                    this.buttonBackExtend.setEnabled(state);
                    break;
                case ADD:
                    this.buttonRefine.setEnabled(state);
                    break;
                case REMOVE:
                    this.buttonRemove.setEnabled(state);
                    break;
                case MOVE:
                    this.buttonMove.setEnabled(state);
                    break;
                case SWITCH:
                    this.buttonSwitch.setEnabled(state);
                    break;
                case UNDO:
                    this.buttonUndo.setEnabled(state);
                    break;
                case INFO:
                    this.buttonInfo.setEnabled(state);
                    break;
                case CHANGE:
                    this.buttonChange.setEnabled(state);
                    break;
                case SAVE:
                    this.buttonSave.setEnabled(state);
                    break;
                case RESAMPLE:
                    this.buttonResample.setEnabled(state);
                    break;
                case EXIT:
                    this.buttonExit.setEnabled(state);
                    break;
            }
        }
    }

    /**
     * Check computer capacity.
     *
     * @param verbose the verbose
     * @return the string[]
     */
    public String[] checkComputerCapacity(boolean verbose) {
        int nbCpu = Runtime.getRuntime().availableProcessors();
        int jvmMemory = (int) ((new Memory().maxMemory() / (1024 * 1024)));//Java virtual machine available memory (in Megabytes)
        long memoryFullSize = 0;
        String[] str = new String[]{"", ""};
        try {
            memoryFullSize = (((OperatingSystemMXBean) ManagementFactory
                    .getOperatingSystemMXBean()).getTotalPhysicalMemorySize()) / (1024 * 1024);
        } catch (Exception e) {
            return str;
        }

        str[0] = "Welcome to RSML Expert \n\t Previous version" + version + "\n\t Current version" + currentVersion;
        str[1] = "System check. Available memory in JVM=" + jvmMemory + " MB over " + memoryFullSize + " MB. #Available processor cores=" + nbCpu + ".";
        if (verbose) return str;
        else return new String[]{"", ""};
    }

    /**
     * Welcome and inform about computer capabilities.
     */
    public void welcomeAndInformAboutComputerCapabilities() {
        String[] str = checkComputerCapacity(true);
        addLog(str[0], 0);
        addLog(str[1], 0);
    }
}