package org.verdictdb.core.scrambling;

import static org.junit.Assert.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.verdictdb.connection.DbmsConnection;
import org.verdictdb.connection.JdbcConnection;
import org.verdictdb.core.execplan.ExecutablePlan;
import org.verdictdb.core.execplan.ExecutablePlanRunner;
import org.verdictdb.core.execplan.ExecutionInfoToken;
import org.verdictdb.core.resulthandler.ExecutionTokenReader;
import org.verdictdb.exception.VerdictDBDbmsException;
import org.verdictdb.exception.VerdictDBException;

public class PartitionMetadataRetrievalNodeTest {

  static Connection conn;

  static DbmsConnection dbmsConn;

  private static final String MYSQL_HOST;

  private static final String MYSQL_DATABASE = "test";

  private static final String MYSQL_UESR = "root";

  private static final String MYSQL_PASSWORD = "";

  private static final String TABLE_NAME = "mytable";

  static {
    String env = System.getenv("BUILD_ENV");
    if (env != null && (env.equals("GitLab") || env.equals("DockerCompose"))) {
      MYSQL_HOST = "mysql";
    } else {
      MYSQL_HOST = "localhost";
    }
  }

  @BeforeClass
  public static void setupMySqlDatabase() throws SQLException, VerdictDBDbmsException {
    String mysqlConnectionString =
        String.format("jdbc:mysql://%s/%s?autoReconnect=true&useSSL=false", MYSQL_HOST, MYSQL_DATABASE);
    conn = DriverManager.getConnection(mysqlConnectionString, MYSQL_UESR, MYSQL_PASSWORD);
    conn.createStatement().execute("DROP TABLE IF EXISTS people");
    conn.createStatement().execute("CREATE TABLE people ("
        + "id smallint, "
        + "name varchar(255), "
        + "gender varchar(8), "
        + "age float, "
        + "height float, "
        + "nation varchar(8), "
        + "birth timestamp) "
        + "partition by key (nation, birth)");
    dbmsConn = JdbcConnection.create(conn);
  }

  @AfterClass
  public static void tearDown() throws SQLException {
    conn.createStatement().execute("DROP TABLE IF EXISTS people");
  }

  @Test
  public void testMethodInvocation() throws VerdictDBException {
    String schemaName = "test";
    String tableName = "people";
    PartitionMetadataRetrievalNode node = PartitionMetadataRetrievalNode.create(
        schemaName, tableName, "tokenKey");
    ExecutablePlan plan = new SimpleTreePlan(node);
    ExecutionTokenReader reader = ExecutablePlanRunner.getTokenReader(dbmsConn, plan);
    ExecutionInfoToken outputToken = reader.next();

    List<String> expected = dbmsConn.getPartitionColumns(schemaName, tableName);
    @SuppressWarnings("unchecked")
    List<String> actual = (List<String>) outputToken.getValue("tokenKey");
    assertEquals(expected, actual);

    //    ExecutablePlanRunner.runTillEnd(dbmsConn, plan);
  }

}
