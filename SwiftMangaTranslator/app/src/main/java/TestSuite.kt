
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    FileManagerTest::class,
    TranslationServiceTest::class
)
class TestSuite
