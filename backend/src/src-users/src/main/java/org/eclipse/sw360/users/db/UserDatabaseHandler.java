/*
 * Copyright Siemens AG, 2013-2017. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.users.db;

import com.cloudant.client.api.CloudantClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.eclipse.sw360.datahandler.cloudantclient.DatabaseConnectorCloudant;
import org.eclipse.sw360.datahandler.common.DatabaseSettings;
import org.eclipse.sw360.datahandler.couchdb.DatabaseConnector;
import org.eclipse.sw360.datahandler.db.UserRepository;
import org.eclipse.sw360.datahandler.db.UserSearchHandler;
import org.eclipse.sw360.datahandler.thrift.*;
import org.eclipse.sw360.datahandler.thrift.users.RequestedAction;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.users.UserGroup;
import org.eclipse.sw360.users.dto.APIResponseRedmine;
import org.eclipse.sw360.users.dto.Issue;
import org.eclipse.sw360.users.dto.RedmineConfigDTO;
import org.eclipse.sw360.users.redmine.ReadFileRedmineConfig;
import org.eclipse.sw360.users.util.FileUtil;
import org.ektorp.http.HttpClient;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.eclipse.sw360.datahandler.permissions.PermissionUtils.makePermission;

/**
 * Class for accessing the CouchDB database
 *
 * @author cedric.bodet@tngtech.com
 */
public class UserDatabaseHandler {

    /**
     * Connection to the couchDB database
     */
    private DatabaseConnectorCloudant db;
    private DatabaseConnector dbConnector;
    private UserRepository repository;
    private UserSearchHandler userSearchHandler;
    private static final Logger log = LogManager.getLogger(UserDatabaseHandler.class);
    private ReadFileRedmineConfig readFileRedmineConfig;
    private static final String SUCCESS = "SUCCESS";
    private static final String FAIL = "FAIL";
    private static final String TITLE = "IMPORT";
    private static boolean IMPORT_DEPARTMENT_STATUS = false;
    private List<String> departmentDuplicate;
    private List<String> emailDoNotExist;
    DateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");

    public UserDatabaseHandler(Supplier<CloudantClient> httpClient, String dbName) throws IOException {
        // Create the connector
        db = new DatabaseConnectorCloudant(httpClient, dbName);
        dbConnector = new DatabaseConnector(DatabaseSettings.getConfiguredHttpClient(), dbName);
        repository = new UserRepository(db);
        readFileRedmineConfig = new ReadFileRedmineConfig();
        userSearchHandler = new UserSearchHandler(dbConnector, httpClient);
    }

    public UserDatabaseHandler(Supplier<CloudantClient> httpClient, Supplier<HttpClient> client, String dbName) throws IOException {
        // Create the connector
        db = new DatabaseConnectorCloudant(httpClient, dbName);
        dbConnector = new DatabaseConnector(client, dbName);
        repository = new UserRepository(db);
        userSearchHandler = new UserSearchHandler(dbConnector, httpClient);
    }

    public User getByEmail(String email) {
        return repository.getByEmail(email);
    }

    public User getUser(String id) {
        return db.get(User.class, id);
    }

    private void prepareUser(User user) throws SW360Exception {
        // Prepare component for database
        ThriftValidate.prepareUser(user);
    }

    public RequestStatus addUser(User user) throws SW360Exception {
        prepareUser(user);
        // Add to database
        db.add(user);

        return RequestStatus.SUCCESS;
    }

    public RequestStatus updateUser(User user) throws SW360Exception {
        prepareUser(user);
        db.update(user);

        return RequestStatus.SUCCESS;
    }

    public RequestStatus deleteUser(User user, User adminUser) {
        if (makePermission(user, adminUser).isActionAllowed(RequestedAction.DELETE)) {
            repository.remove(user);
            return RequestStatus.SUCCESS;
        }
        return RequestStatus.FAILURE;
    }

    public List<User> getAll() {
        return repository.getAll();
    }

    public List<User> searchUsers(String searchText) {
        return userSearchHandler.searchByNameAndEmail(searchText);
    }

    public User getByExternalId(String externalId) {
        return repository.getByExternalId(externalId);
    }

    public User getByApiToken(String token) {
        return repository.getByApiToken(token);
    }

    public Set<String> getUserDepartments() {
        return repository.getUserDepartments();
    }

    public Set<String> getUserEmails() {
        return repository.getUserEmails();
    }

    public List<User> search(String text, Map<String, Set<String>> subQueryRestrictions) {
        return userSearchHandler.search(text, subQueryRestrictions);
    }

    public Map<PaginationData, List<User>> getUsersWithPagination(PaginationData pageData) {
        return repository.getUsersWithPagination(pageData);
    }

    public Set<String> getAllEmailsByDepartmentKey(String departmentKey) {
        Set<String> emails = repository.getEmailsByDepartmentName(departmentKey);
        return emails;
    }


    public RequestSummary importFileToDB(String pathFolder) {
        departmentDuplicate = new ArrayList<>();
        emailDoNotExist = new ArrayList<>();
        List<String> fileNames = new ArrayList<>();
        List<Issue> listIssueSuccess = new ArrayList<>();
        List<Issue> listIssueFail = new ArrayList<>();
        RequestSummary requestSummary = new RequestSummary().setTotalAffectedElements(0).setMessage("");
        RedmineConfigDTO configDTO = readFileRedmineConfig.readFileJson();
        Map<String, List<String>> mapArrayList = new HashMap<>();
        if (IMPORT_DEPARTMENT_STATUS) {
            return requestSummary.setRequestStatus(RequestStatus.PROCESSING);
        }
        IMPORT_DEPARTMENT_STATUS = true;
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        String lastRunningTime = dateFormat.format(calendar.getTime());
        readFileRedmineConfig.writeLastRunningTimeConfig(lastRunningTime);
        try {
            FileUtil.writeLogToFile("", "START IMPORT DEPARTMENT", "", configDTO.getPathFolderLog());
            Set<String> files = FileUtil.listFilesUsingFileWalk(pathFolder);
            for (String file : files) {
                String pathFile = pathFolder + "/" + file;
                String extension = FilenameUtils.getExtension(pathFile);
                if (extension.equalsIgnoreCase("xlsx") || extension.equalsIgnoreCase("xls")) {
                    mapArrayList = readFileExcel(pathFile);
                } else if (extension.equalsIgnoreCase("csv")) {
                    mapArrayList = readFileCsv(pathFile);
                }
                Map<String, User> mapEmail = validateListEmailExistDB(mapArrayList);
                String issueId = pathFile.substring(pathFile.lastIndexOf("_") + 1, pathFile.lastIndexOf("."));
                String fileName = file.replace(pathFile.substring(pathFile.lastIndexOf("_"), pathFile.lastIndexOf(".")), "");
                if (departmentDuplicate.isEmpty() && emailDoNotExist.isEmpty()) {
                    mapArrayList.forEach((k, v) -> v.forEach(email -> updateDepartmentToUser(mapEmail.get(email), k)));
                    Issue issue = new Issue();
                    String joined = mapArrayList.keySet().stream().sorted().collect(Collectors.joining(", "));
                    issue.setIssue_id(issueId);
                    issue.setDescription("DEPARTMENT [" + joined + "] ADDED SUCCESSFULLY - FILE NAME: [" + fileName + "]");
                    listIssueSuccess.add(issue);
                    fileNames.add(pathFile);
                    FileUtil.writeLogToFile(TITLE, "DEPARTMENT [" + joined + "] - FILE NAME: [" + fileName + "]", SUCCESS, configDTO.getPathFolderLog());
                } else {
                    if (!departmentDuplicate.isEmpty()) {
                        Issue issueFail = new Issue();
                        issueFail.setIssue_id(issueId);
                        List<String> departmentDuplicateOrder = departmentDuplicate.stream().sorted().collect(Collectors.toList());
                        String joined = String.join(", ", departmentDuplicateOrder);
                        issueFail.setDescription("DEPARTMENT [" + joined + "] IS DUPLICATE - FILE NAME: [" + fileName + "]");
                        listIssueFail.add(issueFail);
                        FileUtil.writeLogToFile(TITLE, "DEPARTMENT [" + joined + "] IS DUPLICATE - FILE NAME: [" + fileName + "]", FAIL, configDTO.getPathFolderLog());
                        departmentDuplicate = new ArrayList<>();
                    }
                    if (!emailDoNotExist.isEmpty()) {
                        Issue issueFail = new Issue();
                        issueFail.setIssue_id(issueId);
                        List<String> emailDoNotExistOrder = emailDoNotExist.stream().sorted().collect(Collectors.toList());
                        String joined = String.join(", ", emailDoNotExistOrder);
                        issueFail.setDescription("USER [" + joined + "] DOES NOT EXIST - FILE NAME: [" + fileName + "]");
                        listIssueFail.add(issueFail);
                        FileUtil.writeLogToFile(TITLE, "USER [" + joined + "] DOES NOT EXIST - FILE NAME: [" + fileName + "]", FAIL, configDTO.getPathFolderLog());
                        emailDoNotExist = new ArrayList<>();
                    }
                }
            }
            IMPORT_DEPARTMENT_STATUS = false;
            requestSummary.setTotalAffectedElements(listIssueSuccess.size());
            requestSummary.setTotalElements(listIssueSuccess.size() + listIssueFail.size());
            requestSummary.setRequestStatus(RequestStatus.SUCCESS);
        } catch (Exception e) {
            IMPORT_DEPARTMENT_STATUS = false;
            String msg = "Failed to import department";
            requestSummary.setMessage(msg);
            requestSummary.setRequestStatus(RequestStatus.FAILURE);
            FileUtil.writeLogToFile(TITLE, "FILE ERROR: " + e.getMessage(), "", configDTO.getPathFolderLog());
        }
        FileUtil.writeLogToFile(TITLE, "[ FILE SUCCESS: " + listIssueSuccess.size() + " - " + "FILE FAIL: " + listIssueFail.size() + " - " + "TOTAL FILE: " + (listIssueSuccess.size() + listIssueFail.size()) + " ]", "Complete The File Import", configDTO.getPathFolderLog());
        HttpURLConnection connection = responseData(listIssueSuccess, listIssueFail);
        try {
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                FileUtil.writeLogToFile("REDMINE", "Update redmine", SUCCESS, configDTO.getPathFolderLog());
                fileNames.forEach(fileName -> {
                    try {
                        Files.delete(Paths.get(fileName));
                    } catch (IOException e) {
                        log.error("There was an error while deleting the file: {}", e.getMessage());
                        FileUtil.writeLogToFile("Delete file", "File name:" + FilenameUtils.getName(fileName), FAIL, configDTO.getPathFolderLog());
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        FileUtil.writeLogToFile(TITLE, "END IMPORT DEPARTMENT", "", configDTO.getPathFolderLog());

        return requestSummary;
    }

    public Map<String, List<String>> readFileCsv(String filePath) {
        Map<String, List<String>> listMap = new HashMap<>();
        List<String> emailCsv = new ArrayList<>();
        try {
            File file = new File(filePath);
            CSVReader reader = new CSVReaderBuilder(new FileReader(file)).withSkipLines(1).build();
            List<String[]> rows = reader.readAll();
            String mapTemp = "";
            for (String[] row : rows) {
                if (row.length > 1) {
                    if (!Objects.equals(row[0], "")) {
                        if (!mapTemp.isEmpty()) {
                            if (listMap.containsKey(mapTemp)) {
                                departmentDuplicate.add(mapTemp);
                            }
                            listMap.put(mapTemp, emailCsv);
                            emailCsv = new ArrayList<>();
                        }
                        mapTemp = row[0];
                    }
                    String email = row[1];
                    emailCsv.add(email);
                }
            }
            if (listMap.containsKey(mapTemp)) {
                departmentDuplicate.add(mapTemp);
            }
            listMap.put(mapTemp, emailCsv);
        } catch (IOException | CsvException e) {
            log.error("Can't read file csv: {}", e.getMessage());
        }
        return listMap;
    }

    public Map<String, List<String>> readFileExcel(String filePath) {
        Map<String, List<String>> listMap = new HashMap<>();
        List<String> emailExcel = new ArrayList<>();

        Workbook wb = null;
        try (InputStream inp = new FileInputStream(filePath)) {
            wb = WorkbookFactory.create(inp);
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();
            rows.next();
            String mapTemp = "";
            while (rows.hasNext()) {
                Row row = rows.next();
                if (!isRowEmpty(row)) {
                    if (row.getCell(0) != null) {
                        if (!mapTemp.isEmpty()) {
                            if (listMap.containsKey(mapTemp)) {
                                departmentDuplicate.add(mapTemp);
                            }
                            listMap.put(mapTemp, emailExcel);
                            emailExcel = new ArrayList<>();
                        }
                        mapTemp = row.getCell(0).getStringCellValue();
                    }
                    String email = row.getCell(1).getStringCellValue();
                    emailExcel.add(email);
                }
            }
            if (listMap.containsKey(mapTemp)) {
                departmentDuplicate.add(mapTemp);
            }
            listMap.put(mapTemp, emailExcel);
        } catch (Exception ex) {
            log.error("Can't read file excel: {}", ex.getMessage());
        } finally {
            try {
                if (wb != null) wb.close();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
        return listMap;
    }

    public static boolean isRowEmpty(Row row) {
        boolean isEmpty = true;
        DataFormatter dataFormatter = new DataFormatter();
        if (row != null) {
            for (Cell cell : row) {
                if (dataFormatter.formatCellValue(cell).trim().length() > 0) {
                    isEmpty = false;
                    break;
                }
            }
        }
        return isEmpty;
    }

    public Map<String, List<User>> getAllUserByDepartment() {
        List<User> users = repository.getAll();
        Map<String, List<User>> listMap = new HashMap<>();
        for (User user : users) {
            if (user.getSecondaryDepartmentsAndRoles() != null) {
                user.getSecondaryDepartmentsAndRoles().forEach((key, value) -> {
                    if (listMap.containsKey(key)) {
                        List<User> list = listMap.get(key);
                        list.add(user);
                    } else {
                        List<User> list = new ArrayList<>();
                        list.add(user);
                        listMap.put(key, list);
                    }
                });
            }
        }
        return listMap;
    }

    public Map<String, List<User>> searchUsersByDepartment(String departmentKey) {
        Map<String, List<User>> listMap = getAllUserByDepartment();
        Map<String, List<User>> mapByDepartment = new HashMap<>();
        List<User> users;
        for (Map.Entry<String, List<User>> entry : listMap.entrySet()) {
            if (entry.getKey().equals(departmentKey)) {
                users = entry.getValue();
                mapByDepartment.put(entry.getKey(), users);
            }
        }
        return mapByDepartment;
    }

    public String searchUsersByDepartmentToJson(String departmentKey) {
        Set<String> emails = repository.getEmailsByDepartmentName(departmentKey);
        JsonArray departmentJsonArray = new JsonArray();
        for (String email : emails) {
            JsonObject object = new JsonObject();
            object.addProperty("email", email);
            departmentJsonArray.add(object);
        }
        return departmentJsonArray.toString().replace("\\", "");
    }

    public List<String> getAllEmailOtherDepartment(String departmentKey) {
        Set<String> emailsbyDepartment = getAllEmailsByDepartmentKey(departmentKey);
        Set<String> emailByListUser = getUserEmails();

        List<User> users = repository.getAll();
        for (User user : users) {
            emailByListUser.add(user.getEmail());
        }
        List<String> emailOtherDepartment = new ArrayList<>(emailByListUser);
        emailOtherDepartment.removeAll(emailsbyDepartment);
        return emailOtherDepartment;
    }

    public String getAllEmailOtherDepartmentToJson(String departmentKey) {
        JsonArray emailJsonArray = new JsonArray();
        List<String> emailOtherDepartment = getAllEmailOtherDepartment(departmentKey);
        for (String email : emailOtherDepartment) {
            JsonObject object = new JsonObject();
            object.addProperty("email", email);
            emailJsonArray.add(object);
        }
        return emailJsonArray.toString().replace("\\", "");
    }

    public HttpURLConnection responseData(List<Issue> success, List<Issue> fails) {
        RedmineConfigDTO configDTO = readFileRedmineConfig.readFileJson();
        HttpURLConnection conn = null;
        try {
            APIResponseRedmine response = new APIResponseRedmine();
            URL url = new URL(configDTO.getUrlApiRedmine());
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            response.setSuccess(success);
            response.setFail(fails);
            ObjectMapper mapper = new ObjectMapper();
            String arrayToJson = mapper.writeValueAsString(response);
            OutputStream os = conn.getOutputStream();
            os.write(arrayToJson.getBytes());
            os.flush();
            new BufferedReader(new InputStreamReader((conn.getInputStream())));
            conn.disconnect();
        } catch (Exception e) {
            log.info("An error occurred while calling the api : {} ", e.getMessage());
            FileUtil.writeLogToFile("REDMINE", "Update Redmine error: " + e.getMessage() + "", "", configDTO.getPathFolderLog());
        }
        return conn;
    }

    public Map<String, User> validateListEmailExistDB(Map<String, List<String>> mapList) {
        Map<String, User> listUser = new HashMap<>();
        Set<String> setEmail = new HashSet<>();
        mapList.forEach((v, k) -> setEmail.addAll(k));
        for (String email : setEmail) {
            User user = repository.getByEmail(email);
            if (user == null) {
                emailDoNotExist.add(email);
            } else {
                listUser.put(email, user);
            }
        }
        return listUser;
    }

    public void updateDepartmentToUser(User user, String department) {
        Map<String, Set<UserGroup>> map;
        Set<UserGroup> userGroups = new HashSet<>();
        if (user.getSecondaryDepartmentsAndRoles() != null) {
            map = user.getSecondaryDepartmentsAndRoles();
            for (Map.Entry<String, Set<UserGroup>> entry : map.entrySet()) {
                if (entry.getKey().equals(department)) {
                    userGroups = entry.getValue();
                }
            }
        } else {
            map = new HashMap<>();
        }
        userGroups.add(UserGroup.USER);
        map.put(department, userGroups);
        user.setSecondaryDepartmentsAndRoles(map);
        repository.update(user);
    }

    public void updateDepartmentToListUser(List<User> users, String department) {
        users.forEach(u -> {
            User user = repository.getByEmail(u.getEmail());
            updateDepartmentToUser(user, department);
        });
    }

    public void deleteDepartmentByUser(User user, String departmentKey) {
        Map<String, Set<UserGroup>> map = user.getSecondaryDepartmentsAndRoles();
        Set<UserGroup> userGroups = new HashSet<>();
        for (Map.Entry<String, Set<UserGroup>> entry : map.entrySet()) {
            if (entry.getKey().equals(departmentKey)) {
                userGroups = entry.getValue();
            }
        }
        map.remove(departmentKey, userGroups);
        user.setSecondaryDepartmentsAndRoles(map);
        repository.update(user);
    }


    public void deleteDepartmentByListUser(List<User> users, String departmentKey) {
        for (User user : users) {
            deleteDepartmentByUser(user, departmentKey);
        }
    }

    public List<User> getAllUserByListEmail(List<String> emails) {
        List<User> users = new ArrayList<>();
        for (String email : emails) {
            if (getByEmail(email) != null) {
                users.add(getByEmail(email));
            }
        }
        return users;
    }

}

