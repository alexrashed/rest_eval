package at.ac.tuwien.dsg.bakk.rest.jersey.regression;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import at.ac.tuwien.dsg.bakk.rest.jersey.beans.Article;
import at.ac.tuwien.dsg.bakk.rest.jersey.beans.Basket;
import at.ac.tuwien.dsg.bakk.rest.jersey.beans.BasketPage;

public class BasketResourceIT {
	private static Client client;
	private static WebTarget basketTarget;
	private static WebTarget articleTarget;
	private static JacksonJsonProvider jackson_json_provider = new JacksonJaxbJsonProvider()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	@BeforeClass
	public static void setUp() {
		client = ClientBuilder.newClient().register(jackson_json_provider);
		WebTarget target = client.target("http://localhost:8080");
		Response rootResponse = target.request().get();
		articleTarget = client.target(rootResponse.getLink("tos:articles"));
		basketTarget = client.target(rootResponse.getLink("tos:baskets"));
	}

	@Test
	public void testCreateSelfLinkDelete() {
		Response createdResponse = basketTarget.request().post(null);
		Assert.assertEquals(Status.CREATED.getStatusCode(), createdResponse.getStatus());

		Response getResponse = client.target(createdResponse.getLocation()).request().get();
		Assert.assertTrue(getResponse.hasLink("self"));
		Basket linkedBasket = client.target(getResponse.getLink("self")).request().get(Basket.class);
		Assert.assertNotNull(linkedBasket);

		Response delete = client.target(getResponse.getLink("self")).request().delete();
		Assert.assertEquals(Status.OK.getStatusCode(), delete.getStatus());
	}

	@Test
	public void testDelete() {
		Response response = basketTarget.request().post(null);
		Assert.assertEquals(Status.CREATED.getStatusCode(), response.getStatus());

		Response delete = client.target(response.getLocation()).request().delete();
		Assert.assertEquals(Status.OK.getStatusCode(), delete.getStatus());

		Response testGet = client.target(response.getLocation()).request().get();
		Assert.assertEquals(Status.NOT_FOUND.getStatusCode(), testGet.getStatus());
	}

	@Test
	public void testPayBasket() {
		// create a new article
		Article newArticle = new Article("Created", "created...", new BigDecimal(13));
		Response createdArticleResponse = articleTarget.request()
				.post(Entity.entity(newArticle, MediaType.APPLICATION_XML));
		Assert.assertEquals(Status.CREATED.getStatusCode(), createdArticleResponse.getStatus());

		// create a new basket
		Response createBasketResponse = basketTarget.request().post(null);
		Assert.assertEquals(Status.CREATED.getStatusCode(), createBasketResponse.getStatus());
		Basket getBasketResponse = client.target(createBasketResponse.getLocation()).request().get(Basket.class);
		Assert.assertTrue(getBasketResponse.hasLink("self"));
		Assert.assertTrue(getBasketResponse.hasLink("payment"));

		// get the article
		Article getArticleResponse = client.target(createdArticleResponse.getLocation()).request().get(Article.class);

		// find the correct link
		Link addToBasketLink = getArticleResponse.getLink("tos:addToBasket");
		Assert.assertNotNull("There is no link to add the article to the basket", addToBasketLink);

		// add an article to the basket
		Response addArticleToBasketResponse = client
				.target(Link.fromLink(addToBasketLink).baseUri(createdArticleResponse.getLocation()).build()).request()
				.post(null);
		Assert.assertEquals(Status.OK.getStatusCode(), addArticleToBasketResponse.getStatus());

		// pay the basket
		Response payResponse = client.target(getBasketResponse.getLink("payment")).request().post(null);
		Assert.assertEquals(Status.CREATED.getStatusCode(), payResponse.getStatus());

		// find the bill
		Response getBillResponse = client.target(payResponse.getLocation()).request().get();
		Assert.assertEquals(Status.OK.getStatusCode(), getBillResponse.getStatus());

		// delete bill, basket and article
		Response deleteBillResponse = client.target(payResponse.getLocation()).request().delete();
		Assert.assertEquals(Status.OK.getStatusCode(), deleteBillResponse.getStatus());
		Response deleteBasketResponse = client.target(createBasketResponse.getLocation()).request().delete();
		Assert.assertEquals(Status.OK.getStatusCode(), deleteBasketResponse.getStatus());
		Response deleteArticleResponse = client.target(createdArticleResponse.getLocation()).request().delete();
		Assert.assertEquals(Status.OK.getStatusCode(), deleteArticleResponse.getStatus());
	}

	@Test
	public void testPaging() {
		Collection<URI> created = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			Response response = basketTarget.request().post(null);
			Assert.assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
			created.add(response.getLocation());
		}
		Response firstPage = basketTarget.request().get();
		Assert.assertTrue(firstPage.hasLink("next"));
		Assert.assertFalse(firstPage.hasLink("prev"));
		Response secondPage = client.target(firstPage.getLink("next")).request().get();
		Assert.assertTrue(secondPage.hasLink("next"));
		Assert.assertTrue(secondPage.hasLink("prev"));
		BasketPage page = secondPage.readEntity(BasketPage.class);
		Assert.assertNotNull(page);
		Assert.assertTrue(page.getBaskets().size() == 10);

		// go to the last page
		Link next = secondPage.getLink("next");
		Response nextResponse = null;
		for (int i = 0; i < 8; i++) {
			nextResponse = client.target(next.getUri()).request().get();
			next = nextResponse.getLink("next");
		}
		Assert.assertFalse(nextResponse.hasLink("next"));

		for (URI uri : created) {
			Response delete = client.target(uri).request().delete();
			Assert.assertEquals(Status.OK.getStatusCode(), delete.getStatus());
		}
	}
}
