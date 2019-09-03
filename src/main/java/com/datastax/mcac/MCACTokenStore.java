package com.datastax.mcac;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

import ch.qos.logback.core.util.SystemInfo;
import com.datastax.mcac.insights.MCACFingerprint;
import com.datastax.mcac.insights.TokenStore;
import com.google.common.base.Suppliers;
import com.google.common.io.Files;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.statements.ParsedStatement;
import org.apache.cassandra.cql3.statements.SelectStatement;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.exceptions.RequestValidationException;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.transport.messages.ResultMessage;
import org.apache.cassandra.utils.MD5Digest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.cql3.UntypedResultSet;


/*
 *
 * @author Sebastián Estévez on 8/30/19.
 *
 */


/**
 * In order to avoid re-using the same token when a cluster is cloned we do the following:
 *
 * - Store the JWT token associated with a node outside of the data dir. i.e. in a file not a c* table.
 * We do this to avoid the information being backed up.
 *
 */

@Singleton
public class MCACTokenStore implements TokenStore {


    private static final Logger logger = LoggerFactory.getLogger(MCACTokenStore.class);
    private static final String SELECT_ALL_PEERS = "SELECT peer, data_center, host_id, preferred_ip, rack, release_version, rpc_address, schema_version, tokens FROM system.peers;";

    private static Supplier<File> tokenFile;

    private static final String HOST_COLUMN = "node";
    private static final String TOKEN_COLUMN = "bearer_token";
    private static final String LAST_UPDATED = "last_updated";
    private static final String MAX_ADDED_DATE_SEEN_BY_NODE = "max_added_date_seen_by_node";

    private static final QueryProcessor qp =  QueryProcessor.instance;

    private Optional<String> cachedToken = Optional.empty();
    private List<MCACFingerprint.Fingerprint> cachedFingerprint = Collections.EMPTY_LIST;

    @Inject
    public MCACTokenStore(String log_dir)
    {
        this.tokenFile = Suppliers.memoize(() -> new File(log_dir + File.separator + "insights.token"))::get;
    }

    @Override
    public boolean isEmpty()
    {
        try
        {
            return maybeGetTokenFromDisk().isPresent();
        }
        catch (Exception ex)
        {
            logger.warn(
                    "Exception attempting to locate insights token in storage",
                    ex
            );
        }

        return true;
    }

    @Override
    public Optional<String> token()
    {
        return maybeGetTokenFromDisk();
    }

    @Override
    public boolean store(String token)
    {
        assert token != null;

        try
        {
            Optional<String> opToken = Optional.of(token);

            storeTokenOnDisk(opToken);
            cachedToken = opToken;
            return true;
        }
        catch (Exception ex)
        {
            logger.error(
                    "Error attempting to store insights credentials",
                    ex
            );
            return false;
        }
    }


    private void storeTokenOnDisk(Optional<String> replacementToken) throws IOException
    {
        if (!replacementToken.isPresent())
        {
            throw new IllegalStateException("Cannot insert empty credentials!");
        }

        try (FileWriter writer = new FileWriter(tokenFile.get()))
        {
            writer.write(replacementToken.get());
        }

        logger.debug("Stored insights token to {}", tokenFile.get());
    }


    private Optional<String> maybeGetTokenFromDisk()
    {
        if (cachedToken != null && tokenFile.get().exists())
        {
            try
            {
                String token = Files.readFirstLine(tokenFile.get(), Charset.defaultCharset());
                token = token.trim();
                if (!token.isEmpty())
                {
                    cachedToken = (Optional.of(token));
                }
            }
            catch (IOException ex)
            {
                logger.warn("Error reading token file {}", tokenFile.get(), ex);
            }
        }
        else
        {
            logger.trace("Token file missing {}", tokenFile.get());
        }

        return cachedToken;
    }


    public static CQLStatement prepareStatementBlocking(String cql, QueryState queryState, String errorMessage)
    {
        try
        {
            ParsedStatement.Prepared stmt = null;
            while (stmt == null)
            {
                MD5Digest stmtId = qp.prepare(cql, queryState).statementId;
                stmt = qp.getPrepared(stmtId);
            }
            return stmt.statement;

        } catch (RequestValidationException e)
        {
            throw new RuntimeException(errorMessage, e);
        }
    }

    public static UntypedResultSet processPreparedSelect(CQLStatement statement, ConsistencyLevel cl)
    {
        List<ByteBuffer> values = Collections.EMPTY_LIST;
        QueryState queryState= QueryState.forInternalCalls();
        QueryOptions queryOptions = QueryOptions.forInternalCalls(cl, values);

        ResultMessage result = statement.executeInternal(queryState,queryOptions);

        if (result.kind.equals(ResultMessage.Kind.ROWS))
        {
            return UntypedResultSet.create(((ResultMessage.Rows) result).result);
        }

        throw new RuntimeException("Unexpected result type returned for select statement: " + result.kind);
    }

    private UntypedResultSet selectAllTokenFromCQL()
    {
        SelectStatement selectStatement = (SelectStatement) prepareStatementBlocking(
                SELECT_ALL_PEERS,
                QueryState.forInternalCalls(),
                "Error preparing \"" + SELECT_ALL_PEERS + "\""
        );

        return processPreparedSelect(
                selectStatement,
                ConsistencyLevel.LOCAL_ONE
        );
    }

    public void checkFingerprint(UnixSocketClient unixSocketClient) {
        final Optional<String> token = token();

        if (!token.isPresent())
        {
            logger.trace("No token found");
            return;
        }

        UntypedResultSet resultSet = selectAllTokenFromCQL();

        List<MCACFingerprint.Fingerprint> fingerprintList = new ArrayList<>();
        String localHostId = StorageService.instance.getLocalHostId();

        //This node
        fingerprintList.add(new MCACFingerprint.Fingerprint(
                null, null, UUID.fromString(localHostId), null,null, null, null, null, Collections.EMPTY_SET,true
        ));


        for (UntypedResultSet.Row row : resultSet)
        {
            /*
                peer inet PRIMARY KEY,
                data_center text,
                host_id uuid,
                preferred_ip inet,
                rack text,
                release_version text,
                rpc_address inet,
                schema_version uuid,
                tokens set<text>
             */

            InetAddress peer = row.getInetAddress("peer");
            String data_center = row.getString("data_center");
            UUID host_id = row.getUUID("host_id");
            InetAddress preferred_ip = row.getInetAddress("preferred_ip");
            String rack = row.getString("rack");
            String release_version = row.getString("release_version");
            String rpc_address = row.getString("rpc_address");
            UUID schema_version = row.getUUID("schema_version");
            Set<String> tokens = row.getSet("tokens", UTF8Type.instance);


            MCACFingerprint.Fingerprint fingerprint = new MCACFingerprint.Fingerprint(
                    peer, data_center, host_id, preferred_ip, rack, release_version, rpc_address, schema_version, tokens, false
            );

            fingerprintList.add(fingerprint);
        }

        //Filter and Update if we see a newer token
        if (!Objects.equals(this.cachedFingerprint, fingerprintList))
        {
            try {
                if (unixSocketClient.report(new MCACFingerprint(fingerprintList))) {
                    cachedFingerprint = fingerprintList;
                    logger.info("Sent fingerprint to Insights");
                }
            } catch (Exception e) {
                logger.error("Unable to report fingerprints: " + fingerprintList.hashCode(), e);
            }
        }
    }
}
