package net.sansa_stack.spark.cli.main;

import net.sansa_stack.spark.cli.cmd.CmdSansaMain;
import org.aksw.commons.util.exception.ExceptionUtilsAksw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

public class MainCliSansaSpark {

    private static final Logger logger = LoggerFactory.getLogger(CmdSansaMain.class);

    //static { CliUtils.configureGlobalSettings(); }

    public static void main(String[] args) {
        int exitCode = mainCore(args);
        System.exit(exitCode);
    }

    public static int mainCore(String[] args) {
        int result = new CommandLine(new CmdSansaMain())
                .setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
                    CmdSansaMain cmd = commandLine.getCommand();
                    boolean debugMode = cmd.debugMode;
                    if (debugMode) {
                        ExceptionUtilsAksw.rethrowIfNotBrokenPipe(ex);
                    } else {
                        ExceptionUtilsAksw.forwardRootCauseMessageUnless(ex, logger::error, ExceptionUtilsAksw::isBrokenPipeException);
                    }
                    return 0;
                })
                .execute(args);
        return result;
    }

}
