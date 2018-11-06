/*
 * Copyright (C) 2009 Google Inc.
 * Copyright (C) 2010 University of Washington.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.aggregate.externalservice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.opendatakit.aggregate.constants.common.ExternalServicePublicationOption;
import org.opendatakit.aggregate.constants.common.ExternalServiceType;
import org.opendatakit.aggregate.constants.common.OperationalStatus;
import org.opendatakit.aggregate.exception.ODKExternalServiceCredentialsException;
import org.opendatakit.aggregate.exception.ODKExternalServiceException;
import org.opendatakit.aggregate.form.IForm;
import org.opendatakit.aggregate.format.element.BasicElementFormatter;
import org.opendatakit.aggregate.format.element.OhmageJsonElementFormatter;
import org.opendatakit.aggregate.format.header.BasicHeaderFormatter;
import org.opendatakit.aggregate.submission.Submission;
import org.opendatakit.common.persistence.CommonFieldsBase;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;
import org.opendatakit.common.security.common.EmailParser;
import org.opendatakit.common.utils.WebUtils;
import org.opendatakit.common.web.CallingContext;

/**
 * @author wbrunette@gmail.com
 * @author mitchellsundt@gmail.com
 */
public class OhmageJsonServer extends AbstractExternalService implements ExternalService {

  private static final Gson gson;

  static {
    GsonBuilder builder = new GsonBuilder();
    builder.registerTypeAdapter(OhmageJsonTypes.Survey.class, new OhmageJsonTypes.Survey());
    builder.registerTypeAdapter(OhmageJsonTypes.RepeatableSet.class, new OhmageJsonTypes.RepeatableSet());
    builder.serializeNulls();
    builder.setPrettyPrinting();
    gson = builder.create();
  }

  private final OhmageJsonServer2ParameterTable objectEntity;

  private OhmageJsonServer(OhmageJsonServer2ParameterTable entity, FormServiceCursor formServiceCursor, IForm form, CallingContext cc) {
    super(form, formServiceCursor, new BasicElementFormatter(true, true, true, false),
        new BasicHeaderFormatter(true, true, true), cc);
    objectEntity = entity;
  }

  private OhmageJsonServer(OhmageJsonServer2ParameterTable entity, IForm form, ExternalServicePublicationOption externalServiceOption, String ownerEmail, CallingContext cc) throws ODKDatastoreException {
    this(entity, createFormServiceCursor(form, entity, externalServiceOption,
        ExternalServiceType.OHMAGE_JSON_SERVER, cc), form, cc);
    objectEntity.setOwnerEmail(ownerEmail);
  }

  public OhmageJsonServer(FormServiceCursor formServiceCursor, IForm form, CallingContext cc) throws ODKDatastoreException {
    this(retrieveEntity(OhmageJsonServer2ParameterTable.assertRelation(cc), formServiceCursor, cc),
        formServiceCursor, form, cc);
  }

  public OhmageJsonServer(IForm form, String campaignUrn, String campaignTimestamp, String user, String hashedPassword, String serverURL, ExternalServicePublicationOption externalServiceOption, String ownerEmail, CallingContext cc) throws ODKDatastoreException {
    this(newEntity(OhmageJsonServer2ParameterTable.assertRelation(cc), cc), form,
        externalServiceOption, ownerEmail, cc);

    // set stuff to ready for now
    // TODO: check for valid URL
    objectEntity.setOhmageCampaignCreationTimestamp(campaignTimestamp);
    objectEntity.setOhmageCampaignUrn(campaignUrn);
    objectEntity.setOhmageUsername(user);
    objectEntity.setOhmageHashedPassword(hashedPassword);
    objectEntity.setServerUrl(serverURL);
    persist(cc);
  }

  @Override
  public void initiate(CallingContext cc) throws ODKDatastoreException {
    fsc.setIsExternalServicePrepared(true);
    fsc.setOperationalStatus(OperationalStatus.ACTIVE);

    persist(cc);
  }

  @Override
  protected String getOwnership() {
    return objectEntity.getOwnerEmail().substring(EmailParser.K_MAILTO.length());
  }

  public String getServerUrl() {
    return objectEntity.getServerUrl();
  }

  public String getOhmageUsername() {
    return objectEntity.getOhmageUsername();
  }

  public String getOhmageCampaignUrn() {
    return objectEntity.getOhmageCampaignUrn();
  }

  public String getOhmageCampaignCreationTimestamp() {
    return objectEntity.getOhmageCampaignCreationTimestamp();
  }

  public String getOhmageHashedPassword() {
    return objectEntity.getOhmageHashedPassword();
  }

  public void uploadSurveys(List<OhmageJsonTypes.Survey> surveys, Map<UUID, ByteArrayBody> photos, CallingContext cc) throws IOException, ODKExternalServiceException {


    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    builder.setMode(HttpMultipartMode.STRICT)
        .setCharset(UTF_CHARSET);

    ContentType utf8Text = ContentType.create(ContentType.TEXT_PLAIN.getMimeType(), UTF_CHARSET);
    // emit the configured publisher parameters if the values are non-empty...
    String value;
    value = getOhmageCampaignUrn();
    if (value != null && value.length() != 0) {
      builder.addTextBody("campaign_urn", getOhmageCampaignUrn(), utf8Text);
    }
    value = getOhmageCampaignCreationTimestamp();
    if (value != null && value.length() != 0) {
      builder.addTextBody("campaign_creation_timestamp", getOhmageCampaignCreationTimestamp(), utf8Text);
    }
    value = getOhmageUsername();
    if (value != null && value.length() != 0) {
      builder.addTextBody("user", getOhmageUsername(), utf8Text);
    }
    value = getOhmageHashedPassword();
    if (value != null && value.length() != 0) {
      builder.addTextBody("passowrd", getOhmageHashedPassword(), utf8Text);
    }
    // emit the client identity and the json representation of the survey...
    builder.addTextBody("client", cc.getServerURL(), utf8Text);
    builder.addTextBody("survey", gson.toJson(surveys), utf8Text);

    // emit the file streams for all the media attachments
    for (Entry<UUID, ByteArrayBody> entry : photos.entrySet()) {
      builder.addPart(entry.getKey().toString(), entry.getValue());
    }

    HttpResponse response = super.sendHttpRequest(POST, getServerUrl(), builder.build(), null, cc);
    String responseString = WebUtils.readResponse(response);
    int statusCode = response.getStatusLine().getStatusCode();

    if (statusCode == HttpServletResponse.SC_UNAUTHORIZED) {
      throw new ODKExternalServiceCredentialsException("failure from server: " + statusCode
          + " response: " + responseString);
    } else if (statusCode >= 300) {
      throw new ODKExternalServiceException("failure from server: " + statusCode + " response: "
          + responseString);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof OhmageJsonServer)) {
      return false;
    }
    OhmageJsonServer other = (OhmageJsonServer) obj;
    return (objectEntity == null ? (other.objectEntity == null)
        : (other.objectEntity != null && objectEntity.equals(other.objectEntity)))
        && (fsc == null ? (other.fsc == null) : (other.fsc != null && fsc.equals(other.fsc)));
  }

  @Override
  protected void insertData(Submission submission, CallingContext cc) throws ODKExternalServiceException {
    try {
      OhmageJsonTypes.Survey survey = new OhmageJsonTypes.Survey();
      // TODO: figure out these values
      survey.setDate(null);
      survey.setLocation(null);
      survey.setLocation_status(null);
      survey.setSurvey_id(null);
      survey.setSurvey_lauch_context(null);
      survey.setTime(System.currentTimeMillis());
      survey.setTimezone(null);

      OhmageJsonElementFormatter formatter = new OhmageJsonElementFormatter();
      // called purely for side effects
      submission.getFormattedValuesAsRow(null, formatter, false, cc);
      survey.setResponses(formatter.getResponses());

      uploadSurveys(Collections.singletonList(survey), formatter.getPhotos(), cc);

    } catch (ODKExternalServiceCredentialsException e) {
      fsc.setOperationalStatus(OperationalStatus.BAD_CREDENTIALS);
      try {
        persist(cc);
      } catch (Exception ex) {
        ex.printStackTrace();
        throw new ODKExternalServiceException("unable to persist bad credentials state", ex);
      }
      throw e;
    } catch (ODKExternalServiceException e) {
      throw e;// don't wrap these
    } catch (Exception e) {
      throw new ODKExternalServiceException(e);
    }
  }

  @Override
  public String getDescriptiveTargetString() {
    return getServerUrl() + " campaign: " + getOhmageCampaignUrn();
  }

  protected CommonFieldsBase retrieveObjectEntity() {
    return objectEntity;
  }

  @Override
  protected List<? extends CommonFieldsBase> retrieveRepeatElementEntities() {
    return null;
  }

}
