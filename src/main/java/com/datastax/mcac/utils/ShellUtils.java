package com.datastax.mcac.utils;

import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import org.apache.cassandra.io.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//From fallout
public class ShellUtils
{
    private static final Logger logger = LoggerFactory.getLogger(ShellUtils.class);

    private static final Pattern cpuLinePattern = Pattern.compile("^([A-Za-z _-]+[A-Za-z_-])\\s*[:] (.*)$");

    // Lifted from http://stackoverflow.com/a/26538384/322152
    private static final Escaper SHELL_QUOTE_ESCAPER;

    static
    {
        final Escapers.Builder builder = Escapers.builder();
        builder.addEscape(
                '\'',
                "'\"'\"'"
        );
        SHELL_QUOTE_ESCAPER = builder.build();
    }

    public static List<Map<String, String>> loadCpuMap()
    {
        File fCpuInfo = new File("/proc/cpuinfo");
        if (!fCpuInfo.exists())
            throw new IOError(new FileNotFoundException(fCpuInfo.getAbsolutePath()));
        List<String> cpuinfoLines = readLines(fCpuInfo);

        return loadCpuMapFrom(cpuinfoLines);
    }

    public static List<Map<String, String>> loadCpuMapFrom(List<String> lines)
    {
        List<Map<String, String>> cpuMap = new ArrayList<>();
        Map<String, String> info = new HashMap<>();
        for (String cpuinfoLine : lines)
        {
            if (cpuinfoLine.isEmpty())
            {
                cpuMap.add(new HashMap<>());
                info.clear();
            }
            else
            {
                Matcher matcher = cpuLinePattern.matcher(cpuinfoLine);
                if (matcher.matches())
                    info.put(matcher.group(1), matcher.group(2));
            }
        }

        cpuMap.add(info);

        return cpuMap;
    }

    // This is from http://stackoverflow.com/a/20725050/322152.  We're not using org.apache.commons
    // .exec.CommandLine
    // because it fails to parse "run 'echo "foo"'" correctly (v1.3 misses off the final ')
    public static String[] split(CharSequence string)
    {
        List<String> tokens = new ArrayList<>();
        boolean escaping = false;
        char quoteChar = ' ';
        boolean quoting = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < string.length(); i++)
        {
            char c = string.charAt(i);
            if (escaping)
            {
                current.append(c);
                escaping = false;
            }
            else if (c == '\\' && !(quoting && quoteChar == '\''))
            {
                escaping = true;
            }
            else if (quoting && c == quoteChar)
            {
                quoting = false;
            }
            else if (!quoting && (c == '\'' || c == '"'))
            {
                quoting = true;
                quoteChar = c;
            }
            else if (!quoting && Character.isWhitespace(c))
            {
                if (current.length() > 0)
                {
                    tokens.add(current.toString());
                    current = new StringBuilder();
                }
            }
            else
            {
                current.append(c);
            }
        }
        if (current.length() > 0)
        {
            tokens.add(current.toString());
        }
        return tokens.toArray(new String[]{});
    }

    public static String escape(String param)
    {
        return escape(
                param,
                false
        );
    }

    public static String escape(
            String param,
            boolean forceQuote
    )
    {
        String escapedQuotesParam = SHELL_QUOTE_ESCAPER.escape(param);
        return forceQuote || escapedQuotesParam.contains(" ") ?
               "'" + escapedQuotesParam + "'" :
               escapedQuotesParam;
    }

    public static List<String> wrapCommandWithBash(
            String command,
            boolean remoteCommand
    )
    {
        List<String> fullCmd = new ArrayList<>();
        fullCmd.add("/bin/bash");
        fullCmd.add("-o");
        fullCmd.add("pipefail"); // pipe returns first non-zero exit code
        if (remoteCommand)
        {
            // Remote commands should be run in a login shell, since they'll need the environment
            // to be set up correctly.  Local commands should already be in this situation,
            // since fallout should have been run with the correct environment already in place.
            fullCmd.add("-l");
        }
        fullCmd.add("-c"); // execute following command
        if (remoteCommand)
        {
            // Remote commands need to be quoted again, to prevent expansion as they're passed to ssh.
            String escapedCmd = ShellUtils.escape(
                    command,
                    true
            );
            fullCmd.add(escapedCmd);
        }
        else
        {
            fullCmd.add(command);
        }
        return fullCmd;
    }

    public static Process executeShell(
            String command,
            Map<String, String> environment
    )
    {
        List<String> cmds = wrapCommandWithBash(
                command,
                false
        );
        logger.trace(
                "Executing locally: {}, Env {}",
                String.join(
                        " ",
                        cmds
                ),
                environment
        );
        ProcessBuilder pb = new ProcessBuilder(cmds);
        pb.environment().putAll(environment);
        try
        {
            return pb.start();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public interface ThrowingBiFunction<ArgType, Arg2Type, ResType>
    {
        ResType apply(
                ArgType t,
                Arg2Type t2
        ) throws IOException;
    }


    public static <T> T executeShellWithHandlers(
            String command,
            ThrowingBiFunction<BufferedReader, BufferedReader, T> handler,
            ThrowingBiFunction<Integer, BufferedReader, T> errorHandler
    )
            throws IOException
    {
        return executeShellWithHandlers(
                command,
                handler,
                errorHandler,
                Collections.emptyMap()
        );
    }

    public static <T> T executeShellWithHandlers(
            String command,
            ThrowingBiFunction<BufferedReader, BufferedReader, T> handler,
            ThrowingBiFunction<Integer, BufferedReader, T> errorHandler,
            Map<String, String> environment
    )
            throws IOException
    {
        Process ps = ShellUtils.executeShell(
                command,
                environment
        );

        try (BufferedReader input = new BufferedReader(new InputStreamReader(ps.getInputStream()));
             BufferedReader error = new BufferedReader(new InputStreamReader(ps.getErrorStream())))
        {
            ps.waitFor();

            if (ps.exitValue() != 0)
            {
                return errorHandler.apply(
                        ps.exitValue(),
                        error
                );
            }

            return handler.apply(
                    input,
                    error
            );
        }
        catch (InterruptedException t)
        {
            throw new RuntimeException(t);
        }
    }

    public static List<String> readLines(File file)
    {
        try
        {
            return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        }
        catch (IOException ex)
        {
            if (ex instanceof NoSuchFileException)
                return Collections.emptyList();

            throw new RuntimeException(ex);
        }
    }
}

