/*
  Copyright 1995-2013 Esri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

  For additional information, contact:
  Environmental Systems Research Institute, Inc.
  Attn: Contracts Dept
  380 New York Street
  Redlands, California, USA 92373

  email: contracts@esri.com
 */

package com.esri.geoevent.transport.hanadb;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;

import com.esri.ges.core.ConfigurationException;
import com.esri.ges.core.component.ComponentException;
import com.esri.ges.core.component.RunningState;
import com.esri.ges.framework.i18n.BundleLogger;
import com.esri.ges.framework.i18n.BundleLoggerFactory;
import com.esri.ges.transport.OutboundTransportBase;
import com.esri.ges.transport.TransportDefinition;
import java.sql.*;
import java.util.LinkedList;
import java.util.List;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class HanaDBOutboundTransport extends OutboundTransportBase
{
	private static final BundleLogger	LOGGER										= BundleLoggerFactory.getLogger(HanaDBOutboundTransport.class);

	private static final String				JDBC_CONNECTION_STRING				= "jdbcConnectionString";
	private static final String				USER_NAME_PROPERTY				= "outputUserName";
	private static final String				PASSWORD_PROPERTY					= "outputPassword";

	private String										jdbcInfo											= "";
	private String										userName									= "";
	private String										password									= "";
	private String										errorMessage;
	private Connection									conn;


	public HanaDBOutboundTransport(TransportDefinition definition) throws ComponentException
	{
		super(definition);
	}

	protected void readProperties() throws ConfigurationException
	{
		if (hasProperty(JDBC_CONNECTION_STRING))
			jdbcInfo = getProperty(JDBC_CONNECTION_STRING).getValueAsString();
		else
			jdbcInfo = "";

		if (hasProperty(USER_NAME_PROPERTY))
			userName = getProperty(USER_NAME_PROPERTY).getValueAsString();
		else
			userName = "";

		if (hasProperty(PASSWORD_PROPERTY))
			password = getProperty(PASSWORD_PROPERTY).getValueAsString();
		else
			password = "";
	}

	private void applyProperties() throws IOException
	{
		Connection conn = DriverManager.getConnection(jdbcInfo, userName, password);
		if (!conn)
		{
			throw new IOException(LOGGER.translate("AUTHENTICATION_ERROR", jdbcInfo, userName));
		}
	}

	@Override
	public void afterPropertiesSet()
	{
		try
		{
			readProperties();
			if (getRunningState() == RunningState.STARTED)
			{
				cleanup();
				applyProperties();
			}
		}
		catch (Exception error)
		{
			errorMessage = error.getMessage();
			LOGGER.error(errorMessage, error);
			setRunningState(RunningState.ERROR);
		}
	}

	@Override
	public void receive(ByteBuffer buffer, String channelId)
	{
		if (this.getRunningState() == RunningState.STARTED)
		{
			try
			{
				String json = convertToString(buffer);
				JSONObject object = new JSONObject(json);
				String tag = object.getString("tag");
				JSONArray latlong = object.getJSONArray("latlng");
				for (int i = 0; i < latlong.length(); i++)
				{
				    Double lat = latlong.getJSONObject(i).getDouble("lat");
				    Double lon = latlong.getJSONObject(i).getDouble("lng");

				}

				//Need to make generic
				String sql = "INSERT INTO dev_events(tag,lat,lng) VALUES(?,?,?)"
				try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
		            pstmt.setString(1, tag);
		            pstmt.setDouble(2, lat);
		            pstmt.setDouble(2, lng);
		            pstmt.executeUpdate();
		        } catch (SQLException e) {
		            errorMessage = e.getMessage();
					LOGGER.error(errorMessage, error);
		        }
			}
			catch (Exception error)
			{
				LOGGER.error("RECEIVE_ERROR", error.getMessage());
				LOGGER.info(error.getMessage(), error);
			}
		}
	}

	private String convertToString(ByteBuffer buffer)
	{
		try
		{
			CharsetDecoder decoder = getCharsetDecoder();
			CharBuffer charBuffer = decoder.decode(buffer);
			String decodedBuffer = charBuffer.toString();
			return decodedBuffer;
		}
		catch (CharacterCodingException error)
		{
			LOGGER.error("DECODE_ERROR", error.getMessage());
			LOGGER.info(error.getMessage(), error);
			buffer.clear();
			return null;
		}
	}

	private void cleanup()
	{
		errorMessage = "";
		if (conn != null) {
	        try {
	            conn.close();
	        } catch (SQLException e) {
	        	LOGGER.error("CLEANUP_ERROR", e.getMessage());
	        }
    	}
	}

	@Override
	public synchronized void start()
	{
		if (isRunning())
			return;
		try
		{
			this.setRunningState(RunningState.STARTING);
			applyProperties();
			this.setRunningState(RunningState.STARTED);
		}
		catch (IOException error)
		{
			String errorMsg = LOGGER.translate("START_ERROR", error.getMessage());
			LOGGER.error(errorMsg);
			LOGGER.info(error.getMessage(), error);
			errorMessage = error.getMessage();
			this.setRunningState(RunningState.ERROR);
		}
	}

	@Override
	public synchronized void stop()
	{
		this.setRunningState(RunningState.STOPPING);
		cleanup();
		this.setRunningState(RunningState.STOPPED);
	}

	@Override
	public String getStatusDetails()
	{
		return errorMessage;
	}
}
