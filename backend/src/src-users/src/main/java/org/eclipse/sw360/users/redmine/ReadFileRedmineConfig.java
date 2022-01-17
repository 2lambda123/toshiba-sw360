package org.eclipse.sw360.users.redmine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.sw360.users.dto.RedmineConfigDTO;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ReadFileRedmineConfig {

    private static final Logger log = LogManager.getLogger(ReadFileRedmineConfig.class);

    protected String getPathConfig() throws IOException {
        String path = "/";
        File file = File.createTempFile("check", "text");
        String pathFile = file.getPath();
        String[] parts = pathFile.split("/");
        for (int i = 0; i < parts.length; i++) {
            path += parts[i + 1] + "/";
            if (i == 3) return (path + "config.json");
        }
        return (path + "config.json");
    }

    public RedmineConfigDTO readFileJson() {
        try {
            Reader reader = Files.newBufferedReader(Paths.get(getPathConfig()));
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(reader);
            JsonNode configRedmine = jsonNode.path("configRedmine");
            String username = configRedmine.path("username").asText();
            String password = configRedmine.path("password").asText();
            String url = configRedmine.path("url").asText();
            Long projectId = configRedmine.path("projectId").asLong();
            Long trackerId = configRedmine.path("trackerId").asLong();
            Long statusNameOpenId = configRedmine.path("statusNameOpenId").asLong();
            Long statusNameClosedId = configRedmine.path("statusNameClosedId").asLong();
            String pathFolder = configRedmine.path("pathFolder").asText();
            return new RedmineConfigDTO(username, password, url, projectId, trackerId, statusNameOpenId, statusNameClosedId, pathFolder);
        } catch (IOException e) {
            log.error("An I/O error occurred: {}", e.getMessage());
        }
        return null;
    }
}
