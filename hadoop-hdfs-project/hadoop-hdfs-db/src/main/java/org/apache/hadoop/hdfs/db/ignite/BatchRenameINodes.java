package org.apache.hadoop.hdfs.db.ignite;

import java.util.List;
import java.util.TreeMap;
import java.util.Map;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignite;
import org.apache.ignite.lang.IgniteClosure;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.cache.persistence.wal.FileWriteAheadLogManager;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.binary.BinaryObjectBuilder;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.transactions.Transaction;
import org.apache.ignite.transactions.TransactionConcurrency;
import org.apache.ignite.transactions.TransactionIsolation;

public class BatchRenameINodes implements IgniteClosure<List<BinaryObject>, String> {

    @IgniteInstanceResource
    private Ignite ignite;

    @Override
    public String apply(List<BinaryObject> inodes) {
        Map<BinaryObject, BinaryObject> map = new TreeMap<>();
        BinaryObjectBuilder inodeKeyBuilder = ignite.binary().builder("InodeKey");

        Transaction tx = ignite.transactions().txStart(
            TransactionConcurrency.PESSIMISTIC, TransactionIsolation.SERIALIZABLE);

        IgniteCache<BinaryObject, BinaryObject> inodesBinary = ignite.cache("inodes").withKeepBinary();
        for (int i = 0; i < inodes.size(); ++i) {
            BinaryObject inodeKey = inodeKeyBuilder
                .setField("parentName", (String)inodes.get(i).field("parentName"))
                .setField("name", (String)inodes.get(i).field("name"))
                .build();
            map.put(inodeKey, inodes.get(i));
            inodesBinary.query(new SqlFieldsQuery("delete from inodes where id = ?")
                .setArgs(inodes.get(i).field("id")));
        }
        inodesBinary.putAll(map);

        tx.commit();
        tx.close();

        FileWriteAheadLogManager walMgr = (FileWriteAheadLogManager)(
            ((IgniteEx)ignite).context().cache().context().wal());
        return walMgr.lastWritePointer().toString();
    }
}
