package org.jtda;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

public class JtdaCore
{
  private List<StackTrace> traces = new ArrayList<StackTrace>();


  private enum State
  {
    NAME, STATE, STACK, LOCKS, SAVE;
  }

  void analyseSameThread(String input, boolean locks)
  {
    String[] lines = input.split("\n");
    lines = Arrays.copyOf(lines, lines.length + 1);
    lines[lines.length - 1] = "";

    State s = State.NAME;

    // State for this stack trace
    String name = null;
    Thread.State state = null;
    List<String> stack = null;
    List<String> stackIgnoreLocks = null;
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
            stackIgnoreLocks = new ArrayList<String>(1);
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
              stackIgnoreLocks = new ArrayList<String>();
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
            stackIgnoreLocks.add(line.substring(3));
          }
          else if (line.startsWith("- "))
          {
            int startIndex = line.indexOf("<");
            String lineIgnoreLock = null;
            if (startIndex > -1)
            {
              int endIndex = line.indexOf(">", startIndex + 1);
              if (endIndex > -1)
              {
                lineIgnoreLock = line.substring(0, startIndex + 1) +
                          "IGNORED" + line.substring(endIndex, line.length());
              }
            }
            stack.add(line);
            stackIgnoreLocks.add(lineIgnoreLock);
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
              stackIgnoreLocks.add("Locked ownable synchronizers:");
              lSeenOwnedSync = true;
            }
            line = line.substring(2);
            int startIndex = line.indexOf("<");
            String lineIgnoreLocks = null;
            if (startIndex > -1)
            {
              int endIndex = line.indexOf(">", startIndex + 1);
              if (endIndex > -1)
              {
                lineIgnoreLocks = line.substring(0, startIndex + 1) +
                          "IGNORED" + line.substring(endIndex, line.length());
              }
            }
            stack.add(line);
            stackIgnoreLocks.add(lineIgnoreLocks);
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
          traces.add(new StackTrace(name, state, stack, stackIgnoreLocks));

          // Reset state
          s = State.NAME;
        }
        break;
      }
    }
  }

  String calculateOutput(Set<Thread.State> activeStates,
                                 boolean includeNames,
                                 boolean ignoreLocks)
  {
    List<StackTrace> filteredTraces = new ArrayList<StackTrace>();

    // Filter out inactive traces
    for (StackTrace trace : traces)
    {
      if (activeStates.contains(trace.state))
      {
        filteredTraces.add(trace);
      }
    }

    // Count the number of similar traces
    // Stack(ignore-locks) -> Stack(locks) -> StackTrace
    Map<List<String>, Map<List<String>,List<StackTrace>>> stackTraceCounts = new HashMap<List<String>, Map<List<String>,List<StackTrace>>>();
    for (StackTrace trace : filteredTraces)
    {
      Map<List<String>,List<StackTrace>> locksMap = stackTraceCounts.get(trace.stackIgnoreLocks);
      if (locksMap == null)
      {
        locksMap = new HashMap<List<String>, List<StackTrace>>();
        stackTraceCounts.put(trace.stackIgnoreLocks, locksMap);
      }

      List<StackTrace> traces = locksMap.get(trace.stack);
      if (traces == null)
      {
        traces = new ArrayList<StackTrace>();
        locksMap.put(trace.stack, traces);
      }

      traces.add(trace);
    }

    // Sort results
    List<Entry<List<String>, Map<List<String>,List<StackTrace>>>> sortedTraces = new ArrayList<Entry<List<String>, Map<List<String>,List<StackTrace>>>>();
    sortedTraces.addAll(stackTraceCounts.entrySet());
    Collections.sort(sortedTraces, new EntryComparator());

    // Output results
    StringBuilder resultText = new StringBuilder("Total threads: " + filteredTraces.size() + "\n\n");
    for (Entry<List<String>, Map<List<String>,List<StackTrace>>> e : sortedTraces)
    {
      Map<List<String>,List<StackTrace>> traces = e.getValue();
      Map<List<String>,List<StackTrace>> lOutputEntries = new HashMap<List<String>, List<StackTrace>>();

      if (ignoreLocks)
      {
        List<StackTrace> lCombinedTraces = new ArrayList<StackTrace>();
        for (List<StackTrace> lTraces : traces.values())
        {
          lCombinedTraces.addAll(lTraces);
        }
        lOutputEntries.put(e.getKey(), lCombinedTraces);
      }
      else
      {
        lOutputEntries.putAll(e.getValue());
      }

      for (Entry<List<String>, List<StackTrace>> lOutput : lOutputEntries.entrySet())
      {
        resultText.append(lOutput.getValue().size() + " threads with trace:\n");
        outputStates(resultText, lOutput.getValue());
        if (includeNames)
        {
          outputNames(resultText, lOutput.getValue());
        }
        resultText.append("Stack:\n");
        List<String> trace = lOutput.getKey();
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
    }

    return resultText.toString();
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

  private static class StackTrace
  {
    public StackTrace(String name, java.lang.Thread.State state,
                      List<String> stack, List<String> stackIgnoreLocks)
    {
      this.name = name;
      this.state = state;
      this.stack = stack;
      this.stackIgnoreLocks = stackIgnoreLocks;
    }

    public final String name;
    public final Thread.State state;
    public final List<String> stack;
    public final List<String> stackIgnoreLocks;

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

  private static class EntryComparator implements Comparator<Entry<List<String>, Map<List<String>,List<StackTrace>>>>
  {
    @Override
    public int compare(Entry<List<String>, Map<List<String>,List<StackTrace>>> a, Entry<List<String>, Map<List<String>,List<StackTrace>>> b)
    {
      Map<List<String>, List<StackTrace>> valueA = a.getValue();
      Integer countA = 0;
      for (List<?> list : valueA.values())
      {
        countA += list.size();
      }
      Map<List<String>, List<StackTrace>> valueB = b.getValue();
      Integer countB = 0;
      for (List<?> list : valueB.values())
      {
        countB += list.size();
      }
      return -1 * countA.compareTo(countB);
    }
  }
}
