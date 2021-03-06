/*
 * Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.tivi.home

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.ViewModelProviders
import app.tivi.R
import app.tivi.SharedElementHelper
import app.tivi.TiviActivity
import app.tivi.extensions.observeK
import app.tivi.home.HomeActivityViewModel.NavigationItem.DISCOVER
import app.tivi.home.HomeActivityViewModel.NavigationItem.LIBRARY
import app.tivi.home.discover.DiscoverFragment
import app.tivi.home.discover.DiscoverViewModel
import app.tivi.home.followedshows.FollowedShowsFragment
import app.tivi.home.library.LibraryFragment
import app.tivi.home.library.LibraryViewModel
import app.tivi.home.popular.PopularShowsFragment
import app.tivi.home.trending.TrendingShowsFragment
import app.tivi.home.watched.WatchedShowsFragment
import app.tivi.trakt.TraktConstants
import kotlinx.android.synthetic.main.activity_home.*
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import javax.inject.Inject

class HomeActivity : TiviActivity() {
    companion object {
        const val ROOT_FRAGMENT = "root"
    }

    private lateinit var viewModel: HomeActivityViewModel
    private lateinit var navigatorViewModel: HomeNavigatorViewModel

    @Inject lateinit var discoverViewModelFactory: DiscoverViewModel.Factory
    @Inject lateinit var libraryViewModelFactory: LibraryViewModel.Factory

    val authService by lazy(LazyThreadSafetyMode.NONE) {
        AuthorizationService(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        home_content.setOnApplyWindowInsetsListener { view, insets ->
            var consumed = false

            (view as ViewGroup).forEach { child ->
                if (child.dispatchApplyWindowInsets(insets).isConsumed) {
                    consumed = true
                }
            }

            if (consumed) insets.consumeSystemWindowInsets() else insets
        }

        viewModel = ViewModelProviders.of(this, viewModelFactory)
                .get(HomeActivityViewModel::class.java)

        navigatorViewModel = ViewModelProviders.of(this, viewModelFactory)
                .get(HomeNavigatorViewModel::class.java)

        home_bottom_nav.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                home_bottom_nav.selectedItemId -> {
                    if (supportFragmentManager.backStackEntryCount > 0) {
                        for (i in 0 until supportFragmentManager.backStackEntryCount) {
                            supportFragmentManager.popBackStack()
                        }
                    } else {
                        val fragment = supportFragmentManager.findFragmentById(R.id.home_content)
                        when (fragment) {
                            is DiscoverFragment -> fragment.scrollToTop()
                            is LibraryFragment -> fragment.scrollToTop()
                        }
                    }
                    true
                }
                R.id.home_nav_collection -> {
                    viewModel.onNavigationItemClicked(LIBRARY)
                    true
                }
                R.id.home_nav_discover -> {
                    viewModel.onNavigationItemClicked(DISCOVER)
                    true
                }
                else -> false
            }
        }

        viewModel.navigationLiveData.observeK(this, this::showNavigationItem)

        navigatorViewModel.showPopularCall.observeK(this, this::showPopular)
        navigatorViewModel.showTrendingCall.observeK(this, this::showTrending)
        navigatorViewModel.showWatchedCall.observeK(this, this::showWatched)
        navigatorViewModel.showMyShowsCall.observeK(this, this::showMyShows)
        navigatorViewModel.upClickedCall.observeK(this) { this.onUpClicked() }
    }

    private fun showNavigationItem(item: HomeActivityViewModel.NavigationItem?) {
        if (item == null) {
            return
        }

        val newFragment: Fragment
        val newItemId: Int

        when (item) {
            DISCOVER -> {
                newFragment = DiscoverFragment()
                newItemId = R.id.home_nav_discover
            }
            LIBRARY -> {
                newFragment = LibraryFragment()
                newItemId = R.id.home_nav_collection
            }
        }

        for (i in 0 until supportFragmentManager.backStackEntryCount) {
            supportFragmentManager.popBackStack()
        }
        supportFragmentManager
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.home_content, newFragment, ROOT_FRAGMENT)
                .commit()

        // Now make the bottom nav show the correct item
        if (home_bottom_nav.selectedItemId != newItemId) {
            home_bottom_nav.menu.findItem(newItemId)?.isChecked = true
        }
    }

    private fun showPopular(sharedElements: SharedElementHelper?) {
        showStackFragment(PopularShowsFragment(), sharedElements)
    }

    private fun showTrending(sharedElements: SharedElementHelper?) {
        showStackFragment(TrendingShowsFragment(), sharedElements)
    }

    private fun showWatched(sharedElements: SharedElementHelper?) {
        showStackFragment(WatchedShowsFragment(), sharedElements)
    }

    private fun showMyShows(sharedElements: SharedElementHelper?) {
        showStackFragment(FollowedShowsFragment(), sharedElements)
    }

    private fun showStackFragment(fragment: Fragment, sharedElements: SharedElementHelper? = null) {
        supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.home_content, fragment)
                .addToBackStack(null)
                .apply {
                    if (sharedElements != null && !sharedElements.isEmpty()) {
                        sharedElements.applyToTransaction(this)
                    } else {
                        setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    }
                }
                .commit()
    }

    private fun onUpClicked() {
        // TODO can probably do something better here
        supportFragmentManager.popBackStack()
    }

    override fun handleIntent(intent: Intent) {
        when (intent.action) {
            TraktConstants.INTENT_ACTION_HANDLE_AUTH_RESPONSE -> {
                val response = AuthorizationResponse.fromIntent(intent)
                val error = AuthorizationException.fromIntent(intent)
                viewModel.onAuthResponse(authService, response, error)
            }
        }
    }
}
