# Copyright 2019 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
db manages interactions with the underlying database
"""

import logging
import random
from sqlalchemy import create_engine, MetaData, Table, Column, String, Date, LargeBinary
from sqlalchemy.exc import SQLAlchemyError

# Import OpenTelemetry components
from opentelemetry import trace
from opentelemetry.trace import SpanKind

# Initialize tracer
tracer = trace.get_tracer(__name__)

class UserDb:
    """
    UserDb provides a set of helper functions over SQLAlchemy
    to handle db operations for userservice
    """

    def __init__(self, uri, logger=logging):
        with tracer.start_as_current_span("init_userdb", kind=SpanKind.INTERNAL) as span:
            self.engine = create_engine(uri)
            self.logger = logger
            self.users_table = Table(
                'users',
                MetaData(self.engine),
                Column('accountid', String, primary_key=True),
                Column('username', String, unique=True, nullable=False),
                Column('passhash', LargeBinary, nullable=False),
                Column('firstname', String, nullable=False),
                Column('lastname', String, nullable=False),
                Column('birthday', Date, nullable=False),
                Column('timezone', String, nullable=False),
                Column('address', String, nullable=False),
                Column('state', String, nullable=False),
                Column('zip', String, nullable=False),
                Column('ssn', String, nullable=False),
            )
            span.set_attribute("db.system", "sqlalchemy")
            span.set_attribute("db.init", "UserDb initialized")

    def add_user(self, user):
        """Add a user to the database."""
        with tracer.start_as_current_span("add_user", kind=SpanKind.INTERNAL) as span:
            span.set_attribute("db.system", "sqlalchemy")
            span.set_attribute("db.statement", "INSERT INTO users")
            span.set_attribute("user.username", user.get('username'))

            # Step: Prepare SQL statement
            with tracer.start_as_current_span("prepare_add_user_statement") as prepare_span:
                statement = self.users_table.insert().values(user)
                self.logger.debug('QUERY: %s', str(statement))

            # Step: Establish a database connection
            with tracer.start_as_current_span("db_connect_add_user") as conn_span:
                try:
                    with self.engine.connect() as conn:
                        # Step: Execute the query
                        with tracer.start_as_current_span("execute_add_user") as execute_span:
                            conn.execute(statement)

                        # Step: Log successful user addition
                        with tracer.start_as_current_span("log_user_addition") as log_span:
                            self.logger.debug('User added successfully.')
                            span.set_attribute("user.added", True)

                except SQLAlchemyError as err:
                    span.record_exception(err)
                    raise err

    def generate_accountid(self):
        """Generates a globally unique alphanumerical accountid."""
        with tracer.start_as_current_span("generate_accountid", kind=SpanKind.INTERNAL) as span:
            self.logger.debug('Generating an account ID')
            accountid = None
            try:
                # Step: Establish a database connection
                with tracer.start_as_current_span("db_connect_generate_accountid") as conn_span:
                    with self.engine.connect() as conn:
                        while accountid is None:
                            # Step: Generate a random account ID
                            with tracer.start_as_current_span("generate_random_accountid") as gen_span:
                                accountid = str(random.randint(1_000_000_000, (10_000_000_000 - 1)))
                                span.set_attribute("generated_accountid", accountid)

                            # Step: Prepare SQL statement
                            with tracer.start_as_current_span("prepare_accountid_statement") as prepare_span:
                                statement = self.users_table.select().where(
                                    self.users_table.c.accountid == accountid
                                )
                                self.logger.debug('QUERY: %s', str(statement))

                            # Step: Execute the query to check for existing accountid
                            with tracer.start_as_current_span("execute_generate_accountid") as execute_span:
                                result = conn.execute(statement).first()

                            # Step: Check the result and retry if necessary
                            with tracer.start_as_current_span("check_accountid_existence") as check_span:
                                if result is not None:
                                    accountid = None
                                    self.logger.debug('RESULT: account ID already exists. Trying again')
                                    span.add_event("Account ID exists, retrying", attributes={"accountid": accountid})

                self.logger.debug('RESULT: account ID generated.')
                span.set_attribute("generated.accountid", accountid)
            except SQLAlchemyError as err:
                span.record_exception(err)
                raise err
            return accountid

    def get_user(self, username):
        """Get user data for the specified username."""
        with tracer.start_as_current_span("get_user", kind=SpanKind.INTERNAL) as span:
            span.set_attribute("db.system", "sqlalchemy")
            span.set_attribute("db.statement", "SELECT FROM users")
            span.set_attribute("user.username", username)

            # Step: Prepare SQL statement
            with tracer.start_as_current_span("prepare_get_user_statement") as prepare_span:
                statement = self.users_table.select().where(self.users_table.c.username == username)
                self.logger.debug('QUERY: %s', str(statement))

            # Step: Establish a database connection
            with tracer.start_as_current_span("db_connect_get_user") as conn_span:
                try:
                    with self.engine.connect() as conn:
                        # Step: Execute the query
                        with tracer.start_as_current_span("execute_get_user") as execute_span:
                            result = conn.execute(statement).first()

                        # Step: Process the result
                        with tracer.start_as_current_span("process_user_result") as process_span:
                            self.logger.debug('RESULT: fetched user data for %s', username)
                            span.set_attribute("db.user_found", result is not None)
                            return dict(result) if result is not None else None

                except SQLAlchemyError as err:
                    span.record_exception(err)
                    raise err