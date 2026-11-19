# Database schema reference

`schema.sql` is a schema-only snapshot of the authoritative PostgreSQL database. It contains no business rows and is stored outside `src/main/resources`, so Spring Boot cannot execute it automatically. Automatic SQL initialization is also disabled in `application.properties`.

The existing database remains the source of truth. Creating or restoring an empty database is an explicit operator action and must never target an existing environment.

## Create an empty database in the future

1. Back up and verify the source environment using the organization's normal backup process.
2. Create a new, empty PostgreSQL database.
3. Apply the reference only to that empty database:

   ```text
   psql --set ON_ERROR_STOP=1 --host <host> --username <user> --dbname <new-empty-database> --file database/schema.sql
   ```

4. Configure the application through environment variables and start it with `spring.jpa.hibernate.ddl-auto=validate`.
5. Run backend integration tests and API smoke tests before importing any approved business data.

The versioned and legacy migration scripts remain in the repository because no reliable live-database backup was available to satisfy the deletion safety gate. They must not be run against the authoritative database as part of this refactoring.
