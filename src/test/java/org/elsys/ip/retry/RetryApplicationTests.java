package org.elsys.ip.retry;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elsys.ip.retry.AssignmentResultHandler.assignTo;
import static org.hamcrest.core.Is.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RetryApplicationTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void getMissingUser() throws Exception {
        mockMvc.perform(get("/user/123")).andExpect(status().isNotFound());
    }

    @Test
    void createUser() throws Exception {
        createTestUser();
    }

    @Test
    void createAndGetUser() throws Exception {
        Pair<String, String> user = createTestUser();

        mockMvc.perform(get("/user/" + user.getKey())).
                andExpect(status().isOk()).
                andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).
                andExpect(jsonPath("$.name", is(user.getValue()))).
                andExpect(jsonPath("$.credit", is(0))).
                andExpect(jsonPath("$.id", is(user.getKey())));
    }

    @Test
    void createUserTwice() throws Exception {
        String name = createTestUser().getValue();

        mockMvc.perform(post("/user").
                content(name).contentType(MediaType.TEXT_PLAIN)).
                andExpect(status().isBadRequest());
    }

    @Test
    void addCreditMissingUser() throws Exception {
        mockMvc.perform(put("/user/123/credit")).
                andExpect(status().isNotFound());
    }

    @Test
    void createUserAddCredit() throws Exception {
        String userId = createTestUser().getKey();

        mockMvc.perform(put("/user/" + userId + "/credit")).
                andExpect(status().isOk());

        assertThat(getUserCredit(userId)).isEqualTo(10);
    }

    @Test
    void createUserAddCredit100Times() throws Exception {
        String userId = createTestUser().getKey();

        for (int i = 0; i < 100; ++i) {

            mockMvc.perform(put("/user/" + userId + "/credit")).
                    andExpect(status().isOk());

            assertThat(getUserCredit(userId)).isEqualTo(10 * (i + 1));
        }
    }

    @Test
    void createUsersAddCredit() throws Exception {
        String userId1 = createTestUser().getKey();
        String userId2 = createTestUser().getKey();

        mockMvc.perform(put("/user/" + userId1 + "/credit")).
                andExpect(status().isOk());

        assertThat(getUserCredit(userId1)).isEqualTo(10);
        assertThat(getUserCredit(userId2)).isEqualTo(0);

        mockMvc.perform(put("/user/" + userId2 + "/credit")).
                andExpect(status().isOk());

        assertThat(getUserCredit(userId1)).isEqualTo(10);
        assertThat(getUserCredit(userId2)).isEqualTo(10);
    }

    @RepeatedTest(200)
    void createUserAddCreditAndPlay() throws Exception {
        String userId = createTestUser().getKey();

        mockMvc.perform(put("/user/" + userId + "/credit")).
                andExpect(status().isOk());

        assertThat(getUserCredit(userId)).isEqualTo(10);

        AssignmentResult result = new AssignmentResult();
        AssignmentResult win = new AssignmentResult();
        mockMvc.perform(post("/game/").content("{\n" +
                "  \"userId\": \"" + userId + "\",\n" +
                "  " +
                "  \"bet\": \"1\"\n" +
                "}").contentType(MediaType.APPLICATION_JSON)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.userId", is(userId))).
                andExpect(jsonPath("$.bet", is(1))).
                andDo(assignTo("$.result", result)).
                andDo(assignTo("$.win", win));

        assertThat(win.getValue()).isEqualTo(result.getValue().equals(1));

        int newCredit = getUserCredit(userId);

        assertThat(newCredit).isEqualTo((boolean) win.getValue() ? 110 : 9);
    }

    @Test
    void createUserAndPlayNoCredit() throws Exception {
        String userId = createTestUser().getKey();

        mockMvc.perform(post("/game/").content("{\n" +
                "  \"userId\": \"" + userId + "\",\n" +
                "  " +
                "  \"bet\": \"3\"\n" +
                "}").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isExpectationFailed());
    }

    @Test
    void createUserAndPlayWrongBet() throws Exception {
        String userId = createTestUser().getKey();

        mockMvc.perform(put("/user/" + userId + "/credit")).
                andExpect(status().isOk());

        mockMvc.perform(post("/game/").content("{\n" +
                "  \"userId\": \"" + userId + "\",\n" +
                "  " +
                "  \"bet\": \"21\"\n" +
                "}").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());

        mockMvc.perform(post("/game/").content("{\n" +
                "  \"userId\": \"" + userId + "\",\n" +
                "  " +
                "  \"bet\": \"0\"\n" +
                "}").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());

        mockMvc.perform(post("/game/").content("{\n" +
                "  \"userId\": \"" + userId + "\",\n" +
                "  " +
                "  \"bet\": \"-1\"\n" +
                "}").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());

        mockMvc.perform(post("/game/").content("{\n" +
                "  \"userId\": \"" + userId + "\",\n" +
                "  " +
                "  \"bet\": \"1000\"\n" +
                "}").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());

        int credit = getUserCredit(userId);

        assertThat(credit).isEqualTo(10);
    }

    private static int userCount = 0;

    private Pair<String, String> createTestUser() throws Exception {
        String name = "user" + userCount;
        userCount += 1;
        AssignmentResult result = new AssignmentResult();
        mockMvc.perform(post("/user").
                content(name).contentType(MediaType.TEXT_PLAIN)).
                andExpect(status().isCreated()).
                andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).
                andExpect(jsonPath("$.name", is(name))).
                andExpect(jsonPath("$.credit", is(0))).
                andDo(assignTo("$.id", result));
        UUID id = UUID.fromString((String) result.getValue());
        assertThat(id).isNotNull();

        return new Pair<String, String>((String) result.getValue(), name);
    }

    private int getUserCredit(String id) throws Exception {
        AssignmentResult result = new AssignmentResult();
        mockMvc.perform(get("/user/" + id)).
                andExpect(status().isOk()).
                andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).
                andDo(assignTo("$.credit", result));
        return (int) result.getValue();
    }
}
