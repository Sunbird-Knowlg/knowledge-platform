package org.sunbird.cassandra;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Clause;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import com.datastax.driver.core.querybuilder.Select.Where;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.cassandra.enums.CassandraParams;
import org.sunbird.cassandra.store.Constants;
import org.sunbird.common.exception.ServerException;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;

/**
 * @author mahesh
 *
 */
public abstract class CassandraStore {

	protected String keySpace;
	protected String table;
	protected List<String> primaryKey;

	public CassandraStore(String keySpace, String table, List<String> primaryKey) {
		this.keySpace = keySpace;
		this.table = table;
		this.primaryKey = primaryKey;
	}

	public void update(String identifier, Object idValue, Map<String, Object> request) {
		try {
			if (null == request || request.isEmpty()) {
				throw new ServerException(CassandraParams.ERR_SERVER_ERROR.name(),
						"Invalid record to Update.");
			}
			Set<String> keySet = request.keySet();
			String query = getUpdateQueryStatement(identifier, keySet);
			String updateQuery = query + Constants.IF_EXISTS;
			Object[] objects = new Object[request.size() + 1];
			Iterator<String> iterator = keySet.iterator();
			int i = 0;
			while (iterator.hasNext()) {
				objects[i++] = request.get(iterator.next());
			}
			objects[i] = idValue;
			executeQuery(updateQuery, objects);
		} catch (Exception e) {
			e.printStackTrace();
			throw new ServerException(CassandraParams.ERR_SERVER_ERROR.name(),
					"Error while updating record for id : " + idValue, e);
		}
	}

	public void delete(String identifier, Object idValue) {
		try {
			if (StringUtils.isBlank(identifier)) {
				throw new ServerException(CassandraParams.ERR_SERVER_ERROR.name(),
						"Invalid Identifier to delete");
			}
			Delete.Where delete = QueryBuilder.delete().from(keySpace, table).where(eq(identifier, idValue));
			CassandraConnector.getSession().execute(delete);
		} catch (Exception e) {
			throw new ServerException(CassandraParams.ERR_SERVER_ERROR.name(),
					"Error while deleting record for id : " + idValue, e);
		}
	}

	public List<Row> read(String key, Object value) {
		try {
			if (StringUtils.isBlank(key)) {
				throw new ServerException(CassandraParams.ERR_SERVER_ERROR.name(),
						"Invalid Identifier to read");
			}
			Select selectQuery = QueryBuilder.select().all().from(keySpace, table);
			Where selectWhere = selectQuery.where();
			Clause clause = QueryBuilder.eq(key, value);
			selectWhere.and(clause);
			ResultSet results = CassandraConnector.getSession().execute(selectQuery);
			return results.all();
		} catch (Exception e) {
			throw new ServerException(CassandraParams.ERR_SERVER_ERROR.name(),
					"Error while fetching record for ID : " + value, e);
		}

	}

	protected List<Row> getRecordsByProperty(String propertyName, List<Object> propertyValueList) {
		try {
			if (StringUtils.isBlank(propertyName) || propertyValueList.isEmpty()) {
				throw new ServerException(CassandraParams.ERR_SERVER_ERROR.name(),
						"Invalid propertyName to read");
			}
			Select selectQuery = QueryBuilder.select().all().from(keySpace, table);
			Where selectWhere = selectQuery.where();
			Clause clause = QueryBuilder.in(propertyName, propertyValueList);
			selectWhere.and(clause);
			ResultSet results = CassandraConnector.getSession().execute(selectQuery);
			return results.all();
		} catch (Exception e) {
			throw new ServerException(CassandraParams.ERR_SERVER_ERROR.name(),
					"Error while fetching record for Property : " + propertyName, e);
		}
	}

	protected List<Row> getRecordsByProperties(Map<String, Object> propertyMap) {
		try {
			if (null == propertyMap || propertyMap.isEmpty()) {
				throw new ServerException(CassandraParams.ERR_SERVER_ERROR.name(),
						"Invalid propertyName to read");
			}

			Select selectQuery = QueryBuilder.select().all().from(keySpace, table);
			Where selectWhere = selectQuery.where();
			for (Entry<String, Object> entry : propertyMap.entrySet()) {
				if (entry.getValue() instanceof List) {
					Clause clause = QueryBuilder.in(entry.getKey(), Arrays.asList(entry.getValue()));
					selectWhere.and(clause);
				} else {
					Clause clause = QueryBuilder.eq(entry.getKey(), entry.getValue());
					selectWhere.and(clause);
				}
			}
			ResultSet results = CassandraConnector.getSession().execute(selectQuery.allowFiltering());
			return results.all();
		} catch (Exception e) {
			throw new ServerException(CassandraParams.ERR_SERVER_ERROR.name(),
					"Error while fetching records", e);
		}
	}

	protected List<Row> getPropertiesValueById(String identifier, String idValue, String... properties) {
		try {
			if (StringUtils.isBlank(identifier)) {
				throw new ServerException(CassandraParams.ERR_SERVER_ERROR.name(),
						"Invalid Identifier to read");
			}
			String selectQuery = getSelectStatement(identifier, properties);
			PreparedStatement statement = CassandraConnector.getSession().prepare(selectQuery);
			BoundStatement boundStatement = new BoundStatement(statement);
			ResultSet results = CassandraConnector.getSession().execute(boundStatement.bind(identifier));
			return results.all();
		} catch (Exception e) {
			throw new ServerException(CassandraParams.ERR_SERVER_ERROR.name(),
					"Error while fetching properties for ID : " + idValue, e);
		}
	}

	protected List<Row> getAllRecords() {
		try {
			Select selectQuery = QueryBuilder.select().all().from(keySpace, table);
			ResultSet results = CassandraConnector.getSession().execute(selectQuery);
			return results.all();
		} catch (Exception e) {
			throw new ServerException(CassandraParams.ERR_SERVER_ERROR.name(),
					"Error while fetching all records", e);
		}
	}

	protected void upsertRecord(Map<String, Object> request) {
		try {
			if (null == request || request.isEmpty()) {
				throw new ServerException(CassandraParams.ERR_SERVER_ERROR.name(),
						"Invalid Identifier to read");
			}
			Session session = CassandraConnector.getSession();
			String query = getPreparedStatementFrUpsert(request);
			PreparedStatement statement = session.prepare(query);
			BoundStatement boundStatement = new BoundStatement(statement);
			Object[] objects = getBindObjects(request);
			session.execute(boundStatement.bind(objects));

		} catch (Exception e) {
			throw new ServerException(CassandraParams.ERR_SERVER_ERROR.name(), "Error while upsert record",
					e);
		}
	}

	/**
	 * @desc This method is used to create prepared statement based on table
	 *       name and column name provided in request
	 * @return String String
	 */
	private String getPreparedStatement(Set<String> keySet) {
		StringBuilder query = new StringBuilder();
		query.append(Constants.INSERT_INTO + keySpace + Constants.DOT + table + Constants.OPEN_BRACE);
		query.append(String.join(",", keySet) + Constants.VALUES_WITH_BRACE);
		StringBuilder commaSepValueBuilder = new StringBuilder();
		for (int i = 0; i < keySet.size(); i++) {
			commaSepValueBuilder.append(Constants.QUE_MARK);
			if (i != keySet.size() - 1) {
				commaSepValueBuilder.append(Constants.COMMA);
			}
		}
		query.append(commaSepValueBuilder + ")" + Constants.IF_NOT_EXISTS);
		return query.toString();

	}

	/**
	 * @desc This method is used to create update query statement based on table
	 *       name and column name provided
	 */
	private String getUpdateQueryStatement(String id, Set<String> key) {
		StringBuilder query = new StringBuilder(Constants.UPDATE + keySpace + Constants.DOT + table + Constants.SET);
		query.append(String.join(" = ? ,", key));
		query.append(Constants.EQUAL_WITH_QUE_MARK + Constants.WHERE + id + Constants.EQUAL_WITH_QUE_MARK);
		return query.toString();
	}

	/**
	 * @desc This method is used to create prepared statement based on table
	 *       name and column name provided
	 */
	private String getPreparedStatementFrUpsert(Map<String, Object> map) {
		StringBuilder query = new StringBuilder();
		query.append(Constants.INSERT_INTO + keySpace + Constants.DOT + table + Constants.OPEN_BRACE);
		Set<String> keySet = map.keySet();
		query.append(String.join(",", keySet) + Constants.VALUES_WITH_BRACE);
		StringBuilder commaSepValueBuilder = new StringBuilder();
		for (int i = 0; i < keySet.size(); i++) {
			commaSepValueBuilder.append(Constants.QUE_MARK);
			if (i != keySet.size() - 1) {
				commaSepValueBuilder.append(Constants.COMMA);
			}
		}
		query.append(commaSepValueBuilder + Constants.CLOSING_BRACE);
		return query.toString();

	}

	/**
	 * @desc This method is used to create prepared statement based on table
	 *       name and column name provided as varargs
	 */
	private String getSelectStatement(String identifier, String... properties) {
		StringBuilder query = new StringBuilder(Constants.SELECT);
		query.append(String.join(",", properties));
		query.append(Constants.FROM + keySpace + Constants.DOT + table + Constants.WHERE + identifier + Constants.EQUAL
				+ " ?; ");
		return query.toString();
	}

	/**
	 * 
	 * @param query
	 * @param objects
	 * @return
	 */
	private ResultSet executeQuery(String query, Object... objects) {
		Session session = CassandraConnector.getSession();
		PreparedStatement statement = session.prepare(query);
		BoundStatement boundStatement = new BoundStatement(statement);
		return session.execute(boundStatement.bind(objects));
	}

	private Object[] getBindObjects(Map<String, Object> request) {
		Set<String> keySet = request.keySet();
		Iterator<String> iterator = keySet.iterator();
		Object[] objects = new Object[keySet.size()];
		int i = 0;
		while (iterator.hasNext()) {
			objects[i++] = request.get(iterator.next());
		}
		return objects;
	}
	
	/**
	 * @return the keySpace
	 */
	protected String getkeySpace() {
		return keySpace;
	}

	/**
	 * @return the table
	 */
	protected String getTable() {
		return table;
	}

}
