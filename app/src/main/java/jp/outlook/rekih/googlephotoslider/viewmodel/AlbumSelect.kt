package jp.outlook.rekih.googlephotoslider.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.outlook.rekih.googlephotoslider.data.ExternalContents
import jp.outlook.rekih.googlephotoslider.data.GooglePhotoApi
import jp.outlook.rekih.googlephotoslider.model.Album
import kotlinx.coroutines.launch

class AlbumSelect : ViewModel() {
    val albumList: MutableLiveData<List<Album>> by lazy { MutableLiveData<List<Album>>() }
    val loading: MutableLiveData<Boolean> = MutableLiveData<Boolean>(true);

    fun loadAlbumList() {
        viewModelScope.launch {
            loading.value = true
            val albums = GooglePhotoApi.getAlbumList()
                .filter { it.mediaItemsCount.isNotEmpty() }
                .map {
                    it.coverPhotoBitmap = ExternalContents.loadImageBitmap(it.coverPhotoBaseUrl)
                    it
                }
                .toList()
            albumList.value = albums
            loading.value = false
        }
    }
}