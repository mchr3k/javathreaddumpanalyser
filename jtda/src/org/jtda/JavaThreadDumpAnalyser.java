package org.jtda;

import java.util.HashSet;
import java.util.Set;

import net.miginfocom.swt.MigLayout;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;

public class JavaThreadDumpAnalyser
{
  private final Shell window;
  private final Text inputText;
  private final Text outputText;
  private final TabFolder folder;
  private final Button newToggle;
  private final Button runnableToggle;
  private final Button blockedToggle;
  private final Button waitingToggle;
  private final Button timedwaitingToggle;
  private final Button terminatedToggle;
  private final Button analyseButton;
  private final Button namesToggle;
  private final Button ignoreLocksToggle;
  private final Group filterGroup;
  private final Group outputGroup;

  private final JtdaCore data = new JtdaCore();

  public JavaThreadDumpAnalyser(Shell xiWindow)
  {
    window = xiWindow;
    MigLayout mainLayout = new MigLayout("fill", "[grow]", "[grow]");
    window.setLayout(mainLayout);

    folder = new TabFolder(window, SWT.NONE);
    folder.setLayoutData("grow,hmin 0,wmin 0");

    TabItem inputItem = new TabItem(folder, SWT.NONE);
    Composite inputComp = new Composite(folder, SWT.NONE);
    inputComp.setLayoutData("grow,hmin 0,wmin 0");
    inputItem.setControl(inputComp);
    inputItem.setText("Input");
    MigLayout inputLayout = new MigLayout("fill", "[grow]", "[][grow]");
    inputComp.setLayout(inputLayout);

    analyseButton = new Button(inputComp, SWT.PUSH);
    analyseButton.setText("Analyse");
    analyseButton.setLayoutData("wrap");
    inputText = new Text(inputComp, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL
        | SWT.BORDER);
    inputText.setLayoutData("grow,hmin 0,wmin 0");
    inputText.addKeyListener(new KeyAdapter()
    {
      @Override
      public void keyPressed(KeyEvent e)
      {
        if(((e.stateMask & SWT.CTRL) == SWT.CTRL) && (e.keyCode == 'a'))
        {
          inputText.selectAll();
        }
      }
    });

    SelectionListener analyse = new SelectionAdapter()
    {
      @Override
      public void widgetSelected(SelectionEvent arg0)
      {
        analyse();
      }
    };

    analyseButton.addSelectionListener(analyse);

    TabItem outputItem = new TabItem(folder, SWT.NONE);
    Composite outputComp = new Composite(folder, SWT.NONE);
    outputComp.setLayoutData("grow,hmin 0,wmin 0");
    outputItem.setControl(outputComp);
    outputItem.setText("Results");
    MigLayout outputLayout = new MigLayout("fill", "[][][grow]", "[][grow]");
    outputComp.setLayout(outputLayout);

    SelectionListener update = new SelectionAdapter()
    {
      @Override
      public void widgetSelected(SelectionEvent arg0)
      {
        //updateOutput();
      }
    };


    filterGroup = new Group(outputComp, SWT.NONE);
    filterGroup.setText("Filter");
    MigLayout filterLayout = new MigLayout("fill", "[][][]", "[][]");
    filterGroup.setLayout(filterLayout);
    newToggle = new Button(filterGroup, SWT.CHECK);
    newToggle.setText(Thread.State.NEW.toString());
    newToggle.setSelection(true);
    newToggle.addSelectionListener(update);
    runnableToggle = new Button(filterGroup, SWT.CHECK);
    runnableToggle.setText(Thread.State.RUNNABLE.toString());
    runnableToggle.setSelection(true);
    runnableToggle.addSelectionListener(update);
    blockedToggle = new Button(filterGroup, SWT.CHECK);
    blockedToggle.setText(Thread.State.BLOCKED.toString());
    blockedToggle.setSelection(true);
    blockedToggle.addSelectionListener(update);
    blockedToggle.setLayoutData("wrap");
    waitingToggle = new Button(filterGroup, SWT.CHECK);
    waitingToggle.setText(Thread.State.WAITING.toString());
    waitingToggle.setSelection(true);
    waitingToggle.addSelectionListener(update);
    timedwaitingToggle = new Button(filterGroup, SWT.CHECK);
    timedwaitingToggle.setText(Thread.State.TIMED_WAITING.toString());
    timedwaitingToggle.setSelection(true);
    timedwaitingToggle.addSelectionListener(update);
    terminatedToggle = new Button(filterGroup, SWT.CHECK);
    terminatedToggle.setText(Thread.State.TERMINATED.toString());
    terminatedToggle.setSelection(true);
    terminatedToggle.addSelectionListener(update);

    outputGroup = new Group(outputComp, SWT.NONE);
    outputGroup.setText("Output");
    MigLayout outputGroupLayout = new MigLayout("fill", "[]", "[][]");
    outputGroup.setLayout(outputGroupLayout);
    outputGroup.setLayoutData("wrap");
    namesToggle = new Button(outputGroup, SWT.CHECK);
    namesToggle.setText("Show Thread Names");
    namesToggle.setSelection(false);
    namesToggle.addSelectionListener(update);
    namesToggle.setLayoutData("wrap");
    ignoreLocksToggle = new Button(outputGroup, SWT.CHECK);
    ignoreLocksToggle.setText("Ignore Locks");
    ignoreLocksToggle.setSelection(true);
    ignoreLocksToggle.addSelectionListener(update);
    ignoreLocksToggle.setLayoutData("wrap");

    outputText = new Text(outputComp, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL
        | SWT.BORDER);
    outputText.setLayoutData("grow,spanx 6,hmin 0,wmin 0");
    outputText.addKeyListener(new KeyAdapter()
    {
      @Override
      public void keyPressed(KeyEvent e)
      {
        if(((e.stateMask & SWT.CTRL) == SWT.CTRL) && (e.keyCode == 'a'))
        {
          outputText.selectAll();
        }
      }
    });

    inputText.forceFocus();
  }

  public void open()
  {
    window.open();
    Display display = Display.getDefault();
    while (!window.isDisposed())
    {
      if (!display.readAndDispatch())
        display.sleep();
    }
    display.dispose();
  }

  private void analyse()
  {
    analyseButton.setEnabled(false);

    // Capture input
    final String input = inputText.getText();

    // Capture settings
    final boolean ignoreLocks = ignoreLocksToggle.getSelection();
    final boolean includeNames = namesToggle.getSelection();
    final Set<Thread.State> activeStates = new HashSet<Thread.State>();
    if (newToggle.getSelection())
      activeStates.add(Thread.State.NEW);
    if (runnableToggle.getSelection())
      activeStates.add(Thread.State.RUNNABLE);
    if (blockedToggle.getSelection())
      activeStates.add(Thread.State.BLOCKED);
    if (waitingToggle.getSelection())
      activeStates.add(Thread.State.WAITING);
    if (timedwaitingToggle.getSelection())
      activeStates.add(Thread.State.TIMED_WAITING);
    if (terminatedToggle.getSelection())
      activeStates.add(Thread.State.TERMINATED);

    // Kick off thread to analyse input
    Runnable r = new Runnable()
    {
      @Override
      public void run()
      {
        data.analyseSameThread(input, ignoreLocks);
        String output = data.calculateOutput(activeStates, includeNames, ignoreLocks);
        updateOutput(output);
      }
    };
    Thread t = new Thread(r);
    t.setName("Analysis Thread");
    t.setDaemon(true);
    t.start();
  }

  private void updateOutput(final String output)
  {
    window.getDisplay().syncExec(new Runnable()
    {
      @Override
      public void run()
      {
        analyseButton.setEnabled(true);
        outputText.setText(output);
        folder.setSelection(1);
      }
    });
  }

  public static void main(String[] args)
  {
    // Show graphical user interface
    final Shell window = new Shell();
    window.setSize(new Point(650, 600));
    window.setMinimumSize(new Point(650, 600));
    window.setText("Java Thread Dump Analyser");

    // Fill in UI
    JavaThreadDumpAnalyser ui = new JavaThreadDumpAnalyser(window);

    // Open UI
    ui.open();
  }
}