# Serviqo

Serviqo is a Java desktop application for vehicle service and repair workshops. It currently provides sidebar navigation, local SQLite storage, and customer management.

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

## Manage customers

1. Open **Customers** and click **Add customer**. Enter a full name and phone number. Email and address are optional. Saved customers appear in the table with their ID and registration date in UTC.
2. Search by name or by part of the phone number. Clear the search field to show all customers again.
3. Select a row, click **Edit**, and save a change. Close and reopen Serviqo to verify that it was saved.
4. Try saving with a blank name, an invalid phone number, or an incomplete email address. The form should show a validation message and let you correct the field.
5. Select a customer, click **Delete**, and confirm. A customer with a registered vehicle is protected from deletion.

`CustomerPanel.java` handles the customer table, search, and forms. `CustomerRepository.java` uses prepared statements to load and save customer records in SQLite.

If `mvn` is unrecognized, check that Maven's `bin` directory is on the Windows `PATH` and reopen the terminal. If Maven reports a different Java version, check that `JAVA_HOME` points to JDK 21. If Serviqo reports a database startup error, confirm the project folder is writable.
