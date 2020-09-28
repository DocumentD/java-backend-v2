package de.skillkiller.documentdbackend;

import de.skillkiller.documentdbackend.search.MeiliSearch;
import de.skillkiller.documentdbackend.service.MailService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=test"
})
class DocumentdBackendApplicationTests {

    @MockBean
    private MailService mailService;

    @MockBean
    private MeiliSearch meiliSearch;

}
