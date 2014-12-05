package com.netflix.eureka2.server.registry;

import com.netflix.eureka2.datastore.NotificationsSubject;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.SampleInstanceInfo;
import com.netflix.eureka2.server.registry.NotifyingInstanceInfoHolder.NotificationTaskInvoker;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import rx.schedulers.Schedulers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * @author David Liu
 */
public class NotifyingInstanceInfoHolderTest {

    private NotificationsSubject<InstanceInfo> notificationSubject;
    private MultiSourcedDataHolder.HolderStoreAccessor<NotifyingInstanceInfoHolder> storeAccessor;

    @Rule
    public final ExternalResource testResource = new ExternalResource() {
        @Override
        protected void before() throws Throwable {
            notificationSubject = NotificationsSubject.create();
            storeAccessor = new MultiSourcedDataHolder.HolderStoreAccessor<NotifyingInstanceInfoHolder>() {
                @Override
                public void add(NotifyingInstanceInfoHolder holder) {
                }

                @Override
                public NotifyingInstanceInfoHolder get(String id) {
                    return null;
                }

                @Override
                public void remove(String id) {
                }

                @Override
                public boolean contains(String id) {
                    return false;
                }
            };
        }
    };

    private final NotificationTaskInvoker invoker = new NotificationTaskInvoker(Schedulers.computation());

    @Test
    public void testUpdateSameSource() throws Exception {
        InstanceInfo.Builder builder = SampleInstanceInfo.DiscoveryServer.builder();
        InstanceInfo firstInfo = builder
                .withStatus(InstanceInfo.Status.STARTING)
                .build();

        NotifyingInstanceInfoHolder holder = new NotifyingInstanceInfoHolder(storeAccessor, notificationSubject, invoker, firstInfo.getId());
        holder.update(Source.localSource(), firstInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(firstInfo));

        InstanceInfo secondInfo = builder
                .withStatus(InstanceInfo.Status.UP)
                .build();

        holder.update(Source.localSource(), secondInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), not(equalTo(firstInfo)));
        assertThat(holder.get(), equalTo(secondInfo));
    }

    @Test
    public void testUpdateDifferentSources() throws Exception {
        InstanceInfo.Builder builder = SampleInstanceInfo.DiscoveryServer.builder();
        InstanceInfo firstInfo = builder
                .withStatus(InstanceInfo.Status.STARTING)
                .build();

        NotifyingInstanceInfoHolder holder = new NotifyingInstanceInfoHolder(storeAccessor, notificationSubject, invoker, firstInfo.getId());
        holder.update(Source.localSource(), firstInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(firstInfo));

        InstanceInfo secondInfo = builder
                .withStatus(InstanceInfo.Status.UP)
                .build();

        Source fooSource = Source.replicationSource("foo");
        holder.update(fooSource, secondInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(firstInfo));
        assertThat(holder.get(), not(equalTo(secondInfo)));

        assertThat(holder.get(fooSource), equalTo(secondInfo));

        InstanceInfo thirdInfo = builder
                .withStatus(InstanceInfo.Status.DOWN)
                .build();

        holder.update(fooSource, thirdInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(firstInfo));
        assertThat(holder.get(), not(equalTo(secondInfo)));

        assertThat(holder.get(fooSource), equalTo(thirdInfo));
    }

    @Test
    public void testRemoveSameSource() throws Exception {
        InstanceInfo.Builder builder = SampleInstanceInfo.DiscoveryServer.builder();
        InstanceInfo firstInfo = builder
                .withStatus(InstanceInfo.Status.UP)
                .build();

        NotifyingInstanceInfoHolder holder = new NotifyingInstanceInfoHolder(storeAccessor, notificationSubject, invoker, firstInfo.getId());
        holder.update(Source.localSource(), firstInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(firstInfo));

        holder.remove(Source.localSource(), firstInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.size(), equalTo(0));
        assertThat(holder.get(), equalTo(null));
        assertThat(holder.get(Source.localSource()), equalTo(null));
    }

    @Test
    public void testRemoveNonSnapshotCopy() throws Exception {
        InstanceInfo.Builder builder = SampleInstanceInfo.DiscoveryServer.builder();
        InstanceInfo localInfo = builder
                .withStatus(InstanceInfo.Status.STARTING)
                .build();

        NotifyingInstanceInfoHolder holder = new NotifyingInstanceInfoHolder(storeAccessor, notificationSubject, invoker, localInfo.getId());
        holder.update(Source.localSource(), localInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(localInfo));

        InstanceInfo fooInfo = builder
                .withStatus(InstanceInfo.Status.UP)
                .build();

        Source fooSource = Source.replicationSource("foo");
        holder.update(fooSource, fooInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(localInfo));
        assertThat(holder.get(), not(equalTo(fooInfo)));

        assertThat(holder.get(fooSource), equalTo(fooInfo));

        holder.remove(fooSource, fooInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(localInfo));
        assertThat(holder.get(fooSource), equalTo(null));
    }

    @Test
    public void testRemoveSnapshotCopyPromoteAnother() throws Exception {
        InstanceInfo.Builder builder = SampleInstanceInfo.DiscoveryServer.builder();
        InstanceInfo localInfo = builder
                .withStatus(InstanceInfo.Status.STARTING)
                .build();

        NotifyingInstanceInfoHolder holder = new NotifyingInstanceInfoHolder(storeAccessor, notificationSubject, invoker, localInfo.getId());
        holder.update(Source.localSource(), localInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(localInfo));

        InstanceInfo fooInfo = builder
                .withStatus(InstanceInfo.Status.UP)
                .build();

        Source fooSource = Source.replicationSource("foo");
        holder.update(fooSource, fooInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(localInfo));
        assertThat(holder.get(), not(equalTo(fooInfo)));

        assertThat(holder.get(fooSource), equalTo(fooInfo));

        holder.remove(Source.localSource(), localInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(fooInfo));
        assertThat(holder.get(fooSource), equalTo(fooInfo));
        assertThat(holder.get(Source.localSource()), not(equalTo(localInfo)));
    }
}