package org.jtda;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

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
  private List<StackTrace> traces = new ArrayList<StackTrace>();
  private final Button newToggle;
  private final Button runnableToggle;
  private final Button blockedToggle;
  private final Button waitingToggle;
  private final Button timedwaitingToggle;
  private final Button terminatedToggle;
  private final Button analyseButton;
  private final Button namesToggle;
  private final Button ignoreLocks;

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
    MigLayout outputLayout = new MigLayout("fill", "[][][][][][grow]", "[][][grow]");
    outputComp.setLayout(outputLayout);

    SelectionListener update = new SelectionAdapter()
    {
      @Override
      public void widgetSelected(SelectionEvent arg0)
      {
        updateOutput();
      }
    };

    newToggle = new Button(outputComp, SWT.CHECK);
    newToggle.setText(Thread.State.NEW.toString());
    newToggle.setSelection(true);
    newToggle.addSelectionListener(update);
    runnableToggle = new Button(outputComp, SWT.CHECK);
    runnableToggle.setText(Thread.State.RUNNABLE.toString());
    runnableToggle.setSelection(true);
    runnableToggle.addSelectionListener(update);
    blockedToggle = new Button(outputComp, SWT.CHECK);
    blockedToggle.setText(Thread.State.BLOCKED.toString());
    blockedToggle.setSelection(true);
    blockedToggle.addSelectionListener(update);
    namesToggle = new Button(outputComp, SWT.CHECK);
    namesToggle.setText("Include Thread Names");
    namesToggle.setSelection(false);
    namesToggle.addSelectionListener(update);
    namesToggle.setLayoutData("wrap");

    waitingToggle = new Button(outputComp, SWT.CHECK);
    waitingToggle.setText(Thread.State.WAITING.toString());
    waitingToggle.setSelection(true);
    waitingToggle.addSelectionListener(update);
    timedwaitingToggle = new Button(outputComp, SWT.CHECK);
    timedwaitingToggle.setText(Thread.State.TIMED_WAITING.toString());
    timedwaitingToggle.setSelection(true);
    timedwaitingToggle.addSelectionListener(update);
    terminatedToggle = new Button(outputComp, SWT.CHECK);
    terminatedToggle.setText(Thread.State.TERMINATED.toString());
    terminatedToggle.setSelection(true);
    terminatedToggle.addSelectionListener(update);
    ignoreLocks = new Button(outputComp, SWT.CHECK);
    ignoreLocks.setText("Ignore Locks");
    ignoreLocks.setSelection(true);
    ignoreLocks.addSelectionListener(analyse);
    ignoreLocks.setLayoutData("wrap");

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
    final String input = inputText.getText();
    final boolean locks = ignoreLocks.getSelection();
    Runnable r = new Runnable()
    {
      @Override
      public void run()
      {
        analyseSameThread(input, locks);
      }
    };
    Thread t = new Thread(r);
    t.setName("Analysis Thread");
    t.setDaemon(true);
    t.start();
  }

  private enum State
  {
    NAME, STATE, STACK, LOCKS, SAVE;
  }

  private void analyseSameThread(String input, boolean locks)
  {
    String[] lines = input.split("\n");
    lines = Arrays.copyOf(lines, lines.length + 1);
    lines[lines.length - 1] = "";

    State s = State.NAME;

    // State for this stack trace
    String name = null;
    Thread.State state = null;
    List<String> stack = null;
    boolean lSeenOwnedSync = false;

    // Collected stack traces
    traces = new ArrayList<StackTrace>();

    for (int ii = 0; ii < lines.length; ii++)
    {
      String line = lines[ii];
      line = line.trim();
      switch (s)
      {
      case NAME:
      {
        lSeenOwnedSync = false;
        if (line.startsWith("\"") && (line.indexOf('"', 1) > 0))
        {
          s = State.STATE;
          name = line.substring(1, line.indexOf('"', 1));
          state = Thread.State.RUNNABLE;
          stack = new ArrayList<String>(1);
        }
      }
      break;
      case STATE:
      {
        if (line.startsWith("\"") && (line.indexOf('"', 1) > 0))
        {
          ii--;
          s = State.SAVE;
        }
        else if (line.startsWith("java.lang.Thread.State:"))
        {
          String stateStr = line.split(":")[1];
          stateStr = stateStr.trim();
          String[] stateParts = stateStr.split(" ");
          stateStr = stateParts[0];
          try
          {
            state = Thread.State.valueOf(stateStr);
            s = State.STACK;
            stack = new ArrayList<String>(1);
          }
          catch (IllegalArgumentException ex)
          {
            // Ignore
          }
        }
      }
      break;
      case STACK:
      {
        if (line.startsWith("\"") && (line.indexOf('"', 1) > 0))
        {
          ii--;
          s = State.SAVE;
        }
        else if (line.startsWith("at "))
        {
          stack.add(line.substring(3));
        }
        else if (line.startsWith("- "))
        {
          if (locks)
          {
            int startIndex = line.indexOf("<");
            String newline = null;
            if (startIndex > -1)
            {
              int endIndex = line.indexOf(">", startIndex + 1);
              if (endIndex > -1)
              {
                newline = line.substring(0, startIndex + 1) +
                          "IGNORED" + line.substring(endIndex, line.length());
              }
            }
            if (newline != null)
            {
              line = newline;
            }
          }
          stack.add(line);
        }
        else if (line.length() == 0)
        {
          s = State.LOCKS;
        }
      }
      break;
      case LOCKS:
      {
        if (line.startsWith("\"") && (line.indexOf('"', 1) > 0))
        {
          ii--;
          s = State.SAVE;
        }
        else if (line.startsWith("Locked "))
        {
          // Ignore
        }
        else if (line.startsWith("- None"))
        {
          // Ignore
        }
        else if (line.startsWith("- "))
        {
          if (!lSeenOwnedSync)
          {
            stack.add("Locked ownable synchronizers:");
            lSeenOwnedSync = true;
          }
          line = line.substring(2);
          if (locks)
          {
            int startIndex = line.indexOf("<");
            String newline = null;
            if (startIndex > -1)
            {
              int endIndex = line.indexOf(">", startIndex + 1);
              if (endIndex > -1)
              {
                newline = line.substring(0, startIndex + 1) +
                          "IGNORED" + line.substring(endIndex, line.length());
              }
            }
            if (newline != null)
            {
              line = newline;
            }
          }
          stack.add(line);
        }
        else if (line.length() == 0)
        {
          // Save state
          s = State.SAVE;
        }
      }
      break;
      case SAVE:
      {
        ii--;

        // Found stack trace
        traces.add(new StackTrace(name, state, stack));

        // Reset state
        s = State.NAME;
      }
      break;
      }
    }

    updateOutput();
  }

  private void updateOutput()
  {
    List<StackTrace> filteredTraces = new ArrayList<StackTrace>();
    final Set<Thread.State> activeStates = new HashSet<Thread.State>();
    final boolean[] includeNames = new boolean[1];
    window.getDisplay().syncExec(new Runnable()
    {
      @Override
      public void run()
      {
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
        includeNames[0] = namesToggle.getSelection();
      }
    });

    // Filter out inactive traces
    for (StackTrace trace : traces)
    {
      if (activeStates.contains(trace.state))
      {
        filteredTraces.add(trace);
      }
    }

    // Count the number of similar traces
    Map<List<String>, List<StackTrace>> stackTraceCounts = new HashMap<List<String>, List<StackTrace>>();
    for (StackTrace trace : filteredTraces)
    {
      List<StackTrace> commonTraces = stackTraceCounts.get(trace.stack);
      if (commonTraces == null)
      {
        commonTraces = new ArrayList<StackTrace>();
        stackTraceCounts.put(trace.stack, commonTraces);
      }
      commonTraces.add(trace);
    }

    // Sort results
    List<Entry<List<String>, List<StackTrace>>> sortedTraces = new ArrayList<Entry<List<String>,List<StackTrace>>>();
    sortedTraces.addAll(stackTraceCounts.entrySet());
    Collections.sort(sortedTraces, new EntryComparator());

    // Output results
    StringBuilder resultText = new StringBuilder("Total threads: " + filteredTraces.size() + "\n\n");
    for (Entry<List<String>, List<StackTrace>> e : sortedTraces)
    {
      resultText.append(e.getValue().size() + " threads with trace:\n");
      List<String> trace = e.getKey();
      List<StackTrace> traces = e.getValue();
      outputStates(resultText, traces);
      if (includeNames[0])
      {
        outputNames(resultText, traces);
      }
      resultText.append("Stack:\n");
      if (trace.size() == 0)
      {
        resultText.append(" - no stack trace\n");
      }
      else
      {
        for (String traceline : trace)
        {
          if (traceline.startsWith("Locked "))
          {
            resultText.append(" " + traceline + "\n");
          }
          else
          {
            resultText.append(" - " + traceline + "\n");
          }
        }
      }
      resultText.append("\n");
    }

    final String output = resultText.toString();
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

  private void outputStates(StringBuilder resultText, List<StackTrace> traces)
  {
    Map<Thread.State, Integer> stateCount = new TreeMap<Thread.State, Integer>();
    for (StackTrace trace : traces)
    {
      Integer count = stateCount.get(trace.state);
      if (count == null)
      {
        stateCount.put(trace.state, 1);
      }
      else
      {
        stateCount.put(trace.state, count + 1);
      }
    }
    resultText.append("States: " + stateCount.toString() + "\n");
  }

  private void outputNames(StringBuilder resultText, List<StackTrace> traces)
  {
    resultText.append("Names:\n");

    // Sort names
    List<String> threadNames = new ArrayList<String>();
    for (StackTrace trace : traces)
    {
      threadNames.add(trace.name);
    }
    Collections.sort(threadNames);

    // Output names
    for (String name : threadNames)
    {
      resultText.append(" - \"" + name + "\"\n");
    }
  }

  public static void main(String[] args)
  {
    final Shell window = new Shell();
    window.setSize(new Point(650, 600));
    window.setMinimumSize(new Point(650, 600));
    window.setText("Java Thread Dump Analyser");

    // Fill in UI
    JavaThreadDumpAnalyser ui = new JavaThreadDumpAnalyser(window);

    // Open UI
    ui.open();
  }

  private static class StackTrace
  {
    public StackTrace(String name, java.lang.Thread.State state,
                      List<String> stack)
    {
      this.name = name;
      this.state = state;
      this.stack = stack;
    }

    public final String name;
    public final Thread.State state;
    public final List<String> stack;

    @Override
    public String toString()
    {
      StringBuilder str = new StringBuilder();
      str.append("\"" + name + "\"\n");
      str.append(" java.lang.Thread.State: " + state + "\n");
      for (String ele : stack)
      {
        str.append(" at " + ele + "\n");
      }
      str.append("\n");
      return str.toString();
    }
  }

  private static class EntryComparator implements Comparator<Entry<?, List<StackTrace>>>
  {
    @Override
    public int compare(Entry<?, List<StackTrace>> a, Entry<?, List<StackTrace>> b)
    {
      List<StackTrace> valueA = a.getValue();
      Integer countA = valueA.size();
      List<StackTrace> valueB = b.getValue();
      Integer countB = valueB.size();
      return -1 * countA.compareTo(countB);
    }
  }
}