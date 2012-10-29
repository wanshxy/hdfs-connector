
package org.mule.modules.hdfs;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Collections;
import java.util.Map;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.hadoop.fs.ContentSummary;
import org.apache.hadoop.fs.FileChecksum;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.LocalFileSystem;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.modules.hdfs.adapters.HdfsConnectorConnectionIdentifierAdapter;
import org.mule.modules.hdfs.connectivity.HdfsConnectorConnectionKey;
import org.mule.modules.hdfs.connectivity.HdfsConnectorConnectionManager;
import org.mule.tck.junit4.FunctionalTestCase;
import org.mule.util.StringUtils;

public class HdfsConnectorTestCase extends FunctionalTestCase
{
    private boolean localFileSystem;

    @BeforeClass
    public static void setupEnvironment()
    {
        // if no HDFS configuration has been passed as a system property, fallback to
        // local file system
        final String testHdfsFsDefaultName = System.getProperty("test.hdfs.fs.default.name");
        if (StringUtils.isBlank(testHdfsFsDefaultName))
        {
            System.setProperty("test.hdfs.fs.default.name", "file:///");
            System.setProperty("test.hdfs.temp.dir", System.getProperty("java.io.tmpdir"));
        }
    }

    @Override
    protected String getConfigResources()
    {
        return "hdfs-connector-tests-config.xml";
    }

    @Before
    public void initialize() throws Exception
    {
        localFileSystem = isLocalFileSystem();
    }

    @Test
    public void fileSystemOperations() throws Exception
    {
        final String testDirName = "mule-hdfs-test-dir-" + RandomStringUtils.randomAlphanumeric(20);
        final String testFileName = "mule-hdfs-test-file-" + RandomStringUtils.randomAlphanumeric(20)
                                    + ".dat";

        final Map<String, Object> dirNamePathProperties = Collections.<String, Object> singletonMap(
            "hdfsPath", System.getProperty("test.hdfs.temp.dir") + "/" + testDirName);

        // create a directory and assert it exists
        Map<String, Object> metaMap = getPathMetaMap(dirNamePathProperties);
        assertThat((Boolean) metaMap.get("pathExists"), is(false));

        muleContext.getClient().send("vm://makeDirectoriesFlow.in", null, dirNamePathProperties);

        metaMap = getPathMetaMap(dirNamePathProperties);
        assertThat((Boolean) metaMap.get("pathExists"), is(true));
        assertThat(((FileStatus) metaMap.get("fileStatus")).isDir(), is(true));
        assertThat(((ContentSummary) metaMap.get("contentSummary")).getFileCount(), is(0L));

        // create a file, assert it exists and read it back
        final Map<String, Object> fileNamePathProperties = Collections.<String, Object> singletonMap(
            "hdfsPath", "/tmp/" + testDirName + "/" + testFileName);

        final String testInitialData = RandomStringUtils.randomAlphanumeric(20);
        muleContext.getClient().send("vm://writeFlow.in", testInitialData, fileNamePathProperties);

        metaMap = getPathMetaMap(fileNamePathProperties);
        assertThat((Boolean) metaMap.get("pathExists"), is(true));
        assertThat(((FileStatus) metaMap.get("fileStatus")).isDir(), is(false));
        if (!localFileSystem)
        {
            assertThat(metaMap.get("fileChecksum"), is(FileChecksum.class));
        }
        assertThat(((ContentSummary) metaMap.get("contentSummary")).getFileCount(), is(1L));

        final MuleMessage response = muleContext.getClient().send("vm://readFlow.in", null,
            fileNamePathProperties);
        assertThat(response.getPayload(String.class), is(testInitialData));

        // delete existing file and check its gone
        muleContext.getClient().send("vm://deleteFileFlow.in", null, fileNamePathProperties);
        metaMap = getPathMetaMap(fileNamePathProperties);
        assertThat((Boolean) metaMap.get("pathExists"), is(false));

        // re-create the file ...
        muleContext.getClient().send("vm://writeFlow.in", testInitialData, fileNamePathProperties);

        // ... then delete a directory and assert its content and itself are gone
        muleContext.getClient().send("vm://deleteDirectoryFlow.in", null, dirNamePathProperties);

        metaMap = getPathMetaMap(dirNamePathProperties);
        assertThat((Boolean) metaMap.get("pathExists"), is(false));

        metaMap = getPathMetaMap(fileNamePathProperties);
        assertThat((Boolean) metaMap.get("pathExists"), is(false));
    }

    private boolean isLocalFileSystem() throws Exception
    {
        final HdfsConnectorConnectionManager hdfsConnectorConnectionManager = muleContext.getRegistry()
            .lookupObject(HdfsConnectorConnectionManager.class);
        final HdfsConnectorConnectionIdentifierAdapter connection = hdfsConnectorConnectionManager.acquireConnection(new HdfsConnectorConnectionKey(
            "DEFAULT"));
        return connection.getFileSystem() instanceof LocalFileSystem;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPathMetaMap(final Map<String, Object> pathSelectionProperties)
        throws MuleException
    {
        final MuleMessage response = muleContext.getClient().send("vm://pathMetaFlow.in", null,
            pathSelectionProperties);
        return (Map<String, Object>) response.getPayload();
    }
}