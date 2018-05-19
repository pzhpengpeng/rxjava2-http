package org.davidmoten.rx2.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;

public final class ServletHandler {

    private final Random random = new Random();

    private final Map<Long, Subscription> map = new ConcurrentHashMap<>();

    private final Flowable<ByteBuffer> flowable;

    public static ServletHandler create(Flowable<ByteBuffer> flowable) {
        return new ServletHandler(flowable);
    }

    private ServletHandler(Flowable<ByteBuffer> flowable) {
        this.flowable = flowable;
    }

    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        System.out.println(req);
        String idString = req.getParameter("id");
        if (idString == null) {
            final long r = getRequest(req);
            handleStream(resp.getOutputStream(), r);
        } else {
            long id = Long.parseLong(idString);
            long request = Long.parseLong(req.getParameter("r"));
            handleRequest(id, request);
        }
    }

    private void handleStream(OutputStream out, long request) {
        System.out.println("stream");
        CountDownLatch latch = new CountDownLatch(1);
        Runnable completion = () -> latch.countDown();
        long id = random.nextLong();
        Consumer<Subscription> subscription = sub -> map.put(id, sub);
        Handler.handle(flowable, Single.just(out), completion, id, subscription);
        if (request > 0) {
            map.get(id).request(request);
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    private void handleRequest(long id, long request) {
        System.out.println("received request for id=" + id + ", request=" + request);
        Subscription s = map.get(id);
        if (s != null) {
            s.request(request);
        }
    }

    private static long getRequest(HttpServletRequest req) {
        String rString = req.getParameter("r");
        final long r;
        if (rString != null) {
            r = Long.parseLong(rString);
        } else {
            r = 0;
        }
        return r;
    }

    public void destroy() {
        for (Subscription sub : map.values()) {
            sub.cancel();
        }
        map.clear();
    }
}