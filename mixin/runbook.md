# Casssandra Alerts runbooks

## Group Name: "metric-collector-for-apache-cassandra"

### Alert Name: "CassandraReadLatencyHigh"

*Summary*: `Cassandra has high latency for read requests.`
*Severity*: warning

### Alert Name: "CassandraWriteLatencyHigh"

*Summary*: `Cassandra has high latency for write requests.`
*Severity*: warning

### Alert Name: "CassandraTableReadLatencyHigh"

*Summary*: `Cassandra table has high latency for read requests.`
*Severity*: warning

### Alert Name: "CassandraTableWriteLatencyHigh"

*Summary*: `Cassandra table has high latency for write requests.`
*Severity*: warning

### Alert Name: "CassandraCrossNodeLatencyHigh"

*Summary*: `Cassandra has high internode latency.`
*Severity*: warning

### Alert Name: "CassandraDatacenterLatencyHigh"

*Summary*: `Cassandra has high datacenter latency.`
*Severity*: warning

### Alert Name: "CassandraPendingCompactionTasks"

*Summary*: `Cassandra has pending compaction tasks.`
*Severity*: warning

### Alert Name: "CassandraTableDroppedMutations"

*Summary*: `Cassandra table has dropped mutations.`
*Severity*: warning

### Alert Name: "CassandraTablePartitionSizeLarge"

*Summary*: `Cassandra table has large partitions.`
*Severity*: warning
*Action*: Alert development team, as this indicates problems with the data model.

### Alert Name: "CassandraTableLiveSSTableCountHigh"

*Summary*: `Cassandra table has a high count of SSTables.`
*Severity*: warning
*Action*: Too many big tables, which will lead to performance degradation.

### Alert Name: "CassandraStoredHintsHigh"

*Summary*: `Cassandra stored hints is high.`
*Severity*: warning

### Alert Name: "CassandraHintsReplayFailed"

*Summary*: `Cassandra has failed hint replays.`
*Severity*: warning

### Alert Name: "CassandraHintsReplayTimedOut"

*Summary*: `Cassandra has failed hints replays.`
*Severity*: warning

### Alert Name: "CassandraMemtableBlockedOnAllocation"

*Summary*: `Cassandra Memtable has blocked on allocation.`
*Severity*: warning

### Alert Name: "CassandraMemtableBlockedFlushWriterTasks"

*Summary*: `Cassandra Memtable has blocked flush writer tasks.`
*Severity*: warning
*Action*: Investigate. This condition caused by failing disks, excessive disk operations, and so on.

### Alert Name: "CassandraMemtableBlockedCompactorExecutorTasks"

*Summary*: `Cassandra Memtable has blocked compactor exectutor tasks.`
*Severity*: warning

### Alert Name: "CassandraAbortedCompactionTasks"

*Summary*: `Cassandra has aborted compaction tasks.`
*Severity*: warning

### Alert Name: "CassandraJVMGarbageCollectionTimeHigh"

*Summary*: `Cassandra has high JVM garbage collection time`
*Severity*: warning

### Alert Name: "CassandraSegmentsWaitingOnCommit"

*Summary*: `Cassandra has segments waiting on commits.`
*Severity*: warning
*Action*: High count during last minute.

### Alert Name: "CassandraSegmentsWaitingOnCommitDurationHigh"

*Summary*: `Cassandra has spent a long time waiting on commits.`
*Severity*: warning

### Alert Name: "CassandraTablePendingFlushes"

*Summary*: `Cassandra table has pending flushes.`
*Severity*: warning

### Alert Name: "CassandraTableKeyCacheHitRateLow"

*Summary*: `Cassandra table has a low key cache hit rate.`
*Severity*: warning
*Action*: If the cache is full (capacity is equal to size), increase the size of the key cache.

### Alert Name: "CassandraTargetUnreachable"

*Summary*: `Prometheus cannot scrape metrics from Cassandra nodes.`
*Severity*: warning

### Alert Name: "CassandraTargetTooManyUnreachable"

*Summary*: `Prometheus cannot scrape metrics from Cassandra nodes`
*Severity*: critical

### Alert Name: "CassandraCPUUsageHigh"

*Summary*: `Cassandra has high CPU usage.`
*Severity*: warning

### Alert Name: "CassandraLoadHigh"

*Summary*: `Cassandra has high CPU usage.`
*Severity*: warning

### Alert Name: "CassandraDiskUsageHigh"

*Summary*: `Cassandra has high disk usage.`
*Severity*: warning

### Alert Name: "CassandraTableSSTablesPerReadHigh"

*Summary*: `Cassandra table has high number of SSTables accessed per read.`
*Severity*: warning

### Alert Name: "CassandraTableTombstonesPerReadHigh"

*Summary*: `Cassandra table has high number of tombstones scanned per read.`
*Severity*: warning

### Alert Name: "CassandraTablePartitionSize99thPercentileLarge"

*Summary*: `Cassandra table has large partitions.`
*Severity*: warning
