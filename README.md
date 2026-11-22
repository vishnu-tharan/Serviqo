# Serviqo

Serviqo is a Java desktop application for vehicle service and repair workshops. Phase 1 provides the main window, sidebar navigation, and local SQLite database setup.

## Run on Windows

Install JDK 21 and Apache Maven. Open this project folder in VS Code and use its PowerShell terminal, or open PowerShell directly in the project folder. Check the setup with `java -version`, `javac -version`, and `mvn -version`. Maven should report Java 21.

```powershell
mvn compile dependency:copy-dependencies
java -cp 'target/classes;target/dependency/*' com.serviqo.Main
```

Maven downloads the build plugins and SQLite JDBC driver on the first build. After building, use the second command to launch Serviqo without internet access. Run it from the project folder so the app opens the same `serviqo.db` each time.

## Check Phase 1

1. Launch Serviqo and confirm it opens on Dashboard with the message **Database ready**.
2. Click Customers, Vehicles, Service Jobs, and Service History. Check that the heading changes and the selected button is highlighted. Return to Dashboard.
3. Resize the window and check that the sidebar and page heading remain visible.
4. Check that `serviqo.db` exists beside `pom.xml`. Open the database with DB Browser for SQLite if installed. Confirm it contains `customers`, `vehicles`, `service_jobs`, and `service_parts`.
5. Close and reopen Serviqo. Check that it starts successfully using the existing database.

## How it works

`Main.java` starts the application and uses Swing's `CardLayout` to switch screens inside one window. `Database.java` creates the tables in one transaction and enables foreign keys for every connection. The links between tables prevent deleting a parent record while another record still refers to it.

If `mvn` is unrecognized, check that Maven's `bin` directory is on the Windows `PATH` and reopen the terminal. If Maven reports a different Java version, check that `JAVA_HOME` points to JDK 21. If Serviqo reports a database startup error, confirm the project folder is writable.
