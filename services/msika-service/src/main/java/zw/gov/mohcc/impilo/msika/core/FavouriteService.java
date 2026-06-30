package zw.gov.mohcc.impilo.msika.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msika.domain.UlidGenerator;
import zw.gov.mohcc.impilo.msika.persistence.entity.ListingFavouriteEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.ListingFavouriteRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;

/** Buyer favourites — display preference only, scoped to the calling actor. */
@Service
public class FavouriteService {

    private final ListingFavouriteRepository favouriteRepository;

    public FavouriteService(ListingFavouriteRepository favouriteRepository) {
        this.favouriteRepository = favouriteRepository;
    }

    @Transactional
    public void add(String listingId) {
        TrustContext ctx = TrustContextHolder.require();
        if (favouriteRepository.findByActorIdAndListingId(ctx.actorId(), listingId).isPresent()) {
            return;
        }
        ListingFavouriteEntity f = new ListingFavouriteEntity();
        f.setId(UlidGenerator.generate());
        f.setTenantId(ctx.tenantId());
        f.setActorId(ctx.actorId());
        f.setListingId(listingId);
        favouriteRepository.save(f);
    }

    @Transactional
    public void remove(String listingId) {
        TrustContext ctx = TrustContextHolder.require();
        favouriteRepository.deleteByActorIdAndListingId(ctx.actorId(), listingId);
    }

    public List<String> listMine() {
        TrustContext ctx = TrustContextHolder.require();
        return favouriteRepository.findByActorIdOrderByCreatedAtDesc(ctx.actorId())
                .stream().map(ListingFavouriteEntity::getListingId).toList();
    }
}
