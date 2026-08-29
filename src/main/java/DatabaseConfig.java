import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;

public class DatabaseConfig {

    public static DataSource createDataSource(
            String host,
            int port,
            String databaseName,
            String user,
            String password
    ) {
        PGSimpleDataSource dataSource =
                new PGSimpleDataSource();

        dataSource.setServerNames(
                new String[]{host}
        );

        dataSource.setPortNumbers(
                new int[]{port}
        );

        dataSource.setDatabaseName(databaseName);
        dataSource.setUser(user);
        dataSource.setPassword(password);

        return dataSource;
    }
}
