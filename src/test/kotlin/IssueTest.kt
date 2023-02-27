import com.arvgord.repository.entity.AccountEntity
import com.arvgord.repository.entity.ClientEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.Persistence
import jakarta.persistence.criteria.JoinType
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.util.*
import kotlin.random.Random
import kotlin.test.assertEquals

@Testcontainers
internal class IssueTest {

    @Container
    private val postgresqlContainer = PostgreSQLContainer("postgres:15.1")
        .withInitScript("postgres.sql")

    @Test
    fun `test hibernate 6 fetch with in memory pagination`() {
        postgresqlContainer.start()
        val props = Properties()
        props.setProperty("hibernate.connection.url", postgresqlContainer.jdbcUrl)
        val emf = Persistence.createEntityManagerFactory("test", props)
        val em = emf.createEntityManager()
        createClientsAndAccounts(em)

        val criteriaBuilder = em.criteriaBuilder

        val criteriaQueryEntity = criteriaBuilder.createQuery(ClientEntity::class.java)
        val clientsRoot = criteriaQueryEntity.from(ClientEntity::class.java)
        val selectClients = criteriaQueryEntity.select(clientsRoot)
        // Works with WARN: HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
        clientsRoot.fetch<AccountEntity, ClientEntity>("accounts", JoinType.LEFT)
        val firstPage = em.createQuery(selectClients)
            .setFirstResult(0)
            .setMaxResults(2)
            .resultList

        assertEquals(2, firstPage[0].accounts.size)
    }

    @Test
    fun `test hibernate 6 fixed in memory pagination using query splitting`() {
        postgresqlContainer.start()
        val props = Properties()
        props.setProperty("hibernate.connection.url", postgresqlContainer.jdbcUrl)
        val emf = Persistence.createEntityManagerFactory("test", props)
        val em = emf.createEntityManager()
        createClientsAndAccounts(em)

        val criteriaBuilder = em.criteriaBuilder

        // Select main entity ids to except cartesian product
        val criteriaQueryIds = criteriaBuilder.createQuery(Long::class.java)
        val clientsIdsRoot = criteriaQueryIds.from(ClientEntity::class.java)
        val selectIds = criteriaQueryIds.select(clientsIdsRoot.get("id"))

        val mainEntityIdsFirstPage = em.createQuery(selectIds)
            .setFirstResult(0)
            .setMaxResults(2)
            .resultList

        val criteriaQueryEntity = criteriaBuilder.createQuery(ClientEntity::class.java)
        val clientsRoot = criteriaQueryEntity.from(ClientEntity::class.java)
        val inPredicate = criteriaBuilder.`in`(clientsRoot.get<Long>("id"))
        mainEntityIdsFirstPage.forEach { inPredicate.value(it) }
        val selectClients = criteriaQueryEntity.select(clientsRoot).where(inPredicate)
        // Select clients and accounts without in memory pagination
        clientsRoot.fetch<AccountEntity, ClientEntity>("accounts", JoinType.LEFT)
        val firstPage = em.createQuery(selectClients).resultList

        assertEquals(2, firstPage[0].accounts.size)
    }

    private fun createClientsAndAccounts(em: EntityManager) {
        em.transaction.begin()
        repeat(2) {
            val account1 = AccountEntity(
                amount = BigDecimal(Random.nextInt(0,100)),
                number = Random.nextInt().toString()
            )
            val account2 = AccountEntity(
                amount = BigDecimal(Random.nextInt(0,100)),
                number = Random.nextInt().toString()
            )
            val client = ClientEntity(accounts = setOf(account1, account2))
            account1.client = client
            account2.client = client
            em.persist(client)
        }
        em.transaction.commit()
    }
}