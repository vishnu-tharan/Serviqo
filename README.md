# Serviqo

Serviqo is a Java desktop application for vehicle service and repair workshops. It stores customers, registered vehicles, service jobs, and repair charges in a local SQLite database.

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

## Manage vehicles

1. Add the owner in **Customers**. Open **Vehicles**, click **Register vehicle**, and select that owner from the list.
2. Enter the registration number, vehicle type, brand, and model. The manufacturing year and notes are optional. Save and check that the table shows the vehicle and its owner.
3. Search by part of the registration number or owner name. Select a row and click **Edit** to update the vehicle.
4. Try registering the same plate using lowercase letters. The app should report that the registration number is already in use.
5. Reopen Serviqo and check that the vehicle and its changes are still present. In **Customers**, confirm that the owner is protected from deletion while the vehicle is registered.

`VehiclePanel.java` handles the vehicle form, owner selection, and table. `VehicleRepository.java` reads the owner's name from the linked customer record and uses prepared statements to save vehicle details.

## Manage service jobs

1. Register a customer's vehicle. Open **Service Jobs**, click **New job**, and select its registration number. The form shows the linked customer and vehicle details.
2. Enter the reported problem and a service date in `YYYY-MM-DD` format. The repair description is optional. Save the job and confirm it appears as **Pending**.
3. Select the job and click **Open job** to edit the repair description or change the status. Set the job to **In Progress** before marking it **Completed**.
4. A **Pending** or **In Progress** job can be marked **Cancelled**. Completed and cancelled jobs are read-only. Serviqo asks for confirmation before closing either way.
5. Use the **Status** filter to show jobs in a particular state. Reopen Serviqo to confirm that the job, repair description, and status are still saved.

`ServicePanel.java` handles the service job forms and table. `ServiceRepository.java` saves jobs and enforces the status transitions.

## Record parts and labor

1. Select a job in **Service Jobs** and open **Parts and labor**. Charges can be changed while a job is **Pending** or **In Progress**.
2. Click **Add part** and enter a name, a positive whole-number quantity, and a unit price in LKR. Use **Edit part** or **Remove part** to change a saved line.
3. Click **Set labor charge** to record labor in LKR. Prices and charges accept up to two decimal places.
4. Check the calculated line totals, parts subtotal, labor charge, and grand total. For example, two units of engine oil at Rs. 2,000.00 each, one oil filter at Rs. 1,500.00, and Rs. 2,500.00 for labor should total Rs. 8,000.00.
5. Close and reopen the job to confirm the charges were saved. After completing or cancelling the job, reopen **Parts and labor** to view the saved charges in read-only mode.

If `mvn` is unrecognized, check that Maven's `bin` directory is on the Windows `PATH` and reopen the terminal. If Maven reports a different Java version, check that `JAVA_HOME` points to JDK 21. If Serviqo reports a database startup error, confirm the project folder is writable.
