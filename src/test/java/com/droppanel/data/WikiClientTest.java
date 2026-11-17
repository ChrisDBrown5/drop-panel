package com.droppanel.data;

import java.util.ArrayList;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WikiClientTest
{
	private WikiClient.Result fetch(String json) throws Exception
	{
		OkHttpClient http = mock(OkHttpClient.class);
		Call call = mock(Call.class);
		when(http.newCall(any(Request.class))).thenReturn(call);
		doAnswer(invocation ->
		{
			Callback callback = invocation.getArgument(0);
			callback.onResponse(call, new Response.Builder()
				.request(new Request.Builder().url("https://oldschool.runescape.wiki/api.php").build())
				.protocol(Protocol.HTTP_1_1).code(200).message("OK")
				.body(ResponseBody.create(MediaType.parse("application/json"), json)).build());
			return null;
		}).when(call).enqueue(any(Callback.class));
		List<WikiClient.Result> results = new ArrayList<>();
		new WikiClient(http).fetch(2042, results::add);
		assertEquals(1, results.size());
		return results.get(0);
	}

	@Test
	public void validEmptyResponse_isNoData() throws Exception
	{
		assertEquals(WikiClient.Outcome.NO_DATA, fetch("{\"bucket\":[]}").getOutcome());
	}

	@Test
	public void malformedAndApiErrorResponses_areRetryable() throws Exception
	{
		assertEquals(WikiClient.Outcome.ERROR, fetch("<html>Unavailable</html>").getOutcome());
		assertEquals(WikiClient.Outcome.ERROR, fetch("{\"error\":{\"code\":\"ratelimited\"}}").getOutcome());
		assertEquals(WikiClient.Outcome.ERROR, fetch("{}").getOutcome());
	}
}
