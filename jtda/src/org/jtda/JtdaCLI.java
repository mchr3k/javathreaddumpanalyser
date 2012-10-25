package org.jtda;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class JtdaCLI
{

  /**
   * @param args
   */
  public static void main(String[] args) throws IOException
  {
    // Parse args
    CmdArgs parsedArgs = new CmdArgs();

    try
    {
      new JCommander(parsedArgs, args);
    }
    catch (ParameterException ex)
    {
      System.out.println(ex.getMessage());
      System.out.println();
      usage();
      System.exit(1);
    }

    if (parsedArgs.help)
    {
      usage();
      System.exit(0);
    }

    // Handle args
    Set<Thread.State> activeStates = new HashSet<Thread.State>();
    if (parsedArgs.includeStateNEW)
      activeStates.add(Thread.State.NEW);
    if (parsedArgs.includeStateRUNNABLE)
      activeStates.add(Thread.State.RUNNABLE);
    if (parsedArgs.includeStateBLOCKED)
      activeStates.add(Thread.State.BLOCKED);
    if (parsedArgs.includeStateWAITING)
      activeStates.add(Thread.State.WAITING);
    if (parsedArgs.includeStateTIMED_WAITING)
      activeStates.add(Thread.State.TIMED_WAITING);
    if (parsedArgs.includeStateTERMINATED)
      activeStates.add(Thread.State.TERMINATED);

    if (activeStates.isEmpty())
    {
      for (Thread.State state : Thread.State.values())
        activeStates.add(state);
    }

    // Read stdin
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    String lineSep = System.getProperty("line.separator");
    StringBuilder input = new StringBuilder();
    String line = null;
    while ((line = reader.readLine()) != null)
    {
      input.append(line);
      input.append(lineSep);
    }

    // Process data
    JtdaCore cmd = new JtdaCore();
    cmd.analyseSameThread(input.toString(), parsedArgs.ignoreLocks);
    String output = cmd.calculateOutput(activeStates,
                                        !parsedArgs.hideNames,
                                        parsedArgs.ignoreLocks);

    // Output data
    System.out.println(output);
  }

  private static void usage()
  {
    System.out.println("Usage: java -jar jtda-cli.jar [options]");
    System.out.println();
    System.out.println("Options:");
    System.out.println("--ignoreLocks       : Ignore locks in stack traces");
    System.out.println("--hideNames         : Hide thread names");
    System.out.println("--includeNEW <true/false>");
    System.out.println("--includeRUNNABLE <true/false>");
    System.out.println("--includeBLOCKED <true/false>");
    System.out.println("--includeWAITING <true/false>");
    System.out.println("--includeTIMED_WAITING <true/false>");
    System.out.println("--includeTERMINATED <true/false>");
    System.out.println();
    System.out.println("NOTE: All thread states are included by default. If at least one " +
                       "thread state is specified on the commandline only the specified " +
                       "states are included.");
  }
}
