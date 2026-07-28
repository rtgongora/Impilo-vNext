package zw.gov.mohcc.impilo.varapi.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The routing IS the boundary.
 *
 * <p>A contributor is addressed by invitation id and never by application id. That is not a
 * stylistic choice: if a contributor endpoint took an application id, then forgetting one scope
 * check would expose the whole case, and the only thing standing between a training institution and
 * a student's identity documents would be a line of code someone remembered to write.</p>
 *
 * <p>This asserts the shape rather than the behaviour, because the shape is what stops the mistake
 * from being available to make.</p>
 */
class StudentApplicationControllerRoutingTest {

    private static List<String> pathsOf(Method method) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (get != null) return Arrays.asList(get.value());
        if (post != null) return Arrays.asList(post.value());
        return List.of();
    }

    @Test
    void noContributorRouteTakesAnApplicationId() {
        for (Method method : StudentApplicationController.class.getDeclaredMethods()) {
            for (String path : pathsOf(method)) {
                if (path.contains("/contributor/")) {
                    assertThat(path)
                            .as("contributor route %s must not address an application directly", path)
                            .doesNotContain("{applicationId}");
                }
            }
        }
    }

    @Test
    void thereIsAContributorLaneAtAll() {
        // Guards against the test above passing vacuously if the routes are ever renamed away.
        boolean hasContributorRoute = Arrays.stream(
                        StudentApplicationController.class.getDeclaredMethods())
                .flatMap(m -> pathsOf(m).stream())
                .anyMatch(p -> p.contains("/contributor/"));
        assertThat(hasContributorRoute).isTrue();
    }
}
