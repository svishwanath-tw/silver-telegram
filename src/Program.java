import com.thoughtworks.mingle.client.MingleClientConstants;
import com.thoughtworks.mingle.client.MingleRequestSigningInterceptor;
import com.thoughtworks.mingle.client.MingleUrlHelper;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

public class Program {

    private final static String ACCESS_KEY_ID = "your access key id";
    private final static String SECRET_ACCESS_KEY = "your secret key here";
    private final static String MINGLE_BASE_URL = "https://your.mingle.url";

    public static void main(String[] args)  {
        CloseableHttpClient httpClient = null;
        try {
            httpClient = HttpClients.custom().addInterceptorFirst(new MingleRequestSigningInterceptor(ACCESS_KEY_ID, SECRET_ACCESS_KEY)).build();
            String requestPath;
            requestPath = "/projects/a_new_project/cards.xml";

            String requestUrl = new MingleUrlHelper(MINGLE_BASE_URL).getRequestUrl(requestPath);

            String cardTitle = "New card title";
            String cardUrl = createCard(httpClient, requestUrl, cardTitle);
            if(cardUrl != null) {
                System.out.println("Card created successfully. Fetching details\n----------------");
                showCard(httpClient, cardUrl);
                System.out.println("\n---------------------------");

            }
            httpClient.close();
        } catch (Exception e) {
            System.out.println("Error occurred : " + e.getMessage());
        }
    }

    private static String createCard(CloseableHttpClient httpClient, String requestUrl, String cardTitle) throws Exception {
        CloseableHttpResponse httpResponse;
        String cardUrl;
        String xmlString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "  <card>\n" +
                "    <name>" + cardTitle + "</name>\n" +
                "    <type>Story</type>\n" +
                "  </card>\n" ;

        HttpPost httpPost = new HttpPost(requestUrl);
        httpPost.setHeader(new BasicHeader(MingleClientConstants.CONTENT_TYPE_HEADER_NAME, MingleClientConstants.XML_CONTENT_TYPE));
        httpPost.setEntity(new StringEntity(xmlString));
        httpResponse = httpClient.execute(httpPost);

        int statusCode = httpResponse.getStatusLine().getStatusCode();

        if(statusCode >= 200 && statusCode < 300)
            cardUrl = httpResponse.getHeaders(MingleClientConstants.LOCATION_HEADER_NAME)[0].getValue();
        else {
            String responseContent = EntityUtils.toString(httpResponse.getEntity());
            throw new Exception("Card creation failed: Response content \n" + responseContent);
        }
        httpResponse.close();
        return cardUrl;
    }

    private static void showCard(CloseableHttpClient httpClient, String requestUrl) throws IOException {
        CloseableHttpResponse httpResponse;
        HttpGet httpGet = new HttpGet(requestUrl);
        httpResponse = httpClient.execute(httpGet);
        System.out.println(EntityUtils.toString(httpResponse.getEntity()));
        httpResponse.close();
    }
}
