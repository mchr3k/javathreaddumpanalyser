package org.jtda;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Summarise a Java Thread Dump")
public class CmdArgs
{
  @Parameter(names = "--ignoreLocks")
  public boolean ignoreLocks = false;

  @Parameter(names = "--hideNames")
  public boolean hideNames = false;

  @Parameter(names = "--includeNEW", arity = 1)
  public boolean includeStateNEW = false;

  @Parameter(names = "--includeRUNNABLE", arity = 1)
  public boolean includeStateRUNNABLE = false;

  @Parameter(names = "--includeBLOCKED", arity = 1)
  public boolean includeStateBLOCKED = false;

  @Parameter(names = "--includeWAITING", arity = 1)
  public boolean includeStateWAITING = false;

  @Parameter(names = "--includeTIMED_WAITING", arity = 1)
  public boolean includeStateTIMED_WAITING = false;

  @Parameter(names = "--includeTERMINATED", arity = 1)
  public boolean includeStateTERMINATED = false;

  @Parameter(names = "--help", help = true)
  public boolean help;
}
