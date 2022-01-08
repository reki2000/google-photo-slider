package jp.outlook.rekih.googlephotoslider.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.outlook.rekih.googlephotoslider.data.Album
import jp.outlook.rekih.googlephotoslider.data.GooglePhotoApi
import kotlinx.coroutines.launch

class AlbumSelect : ViewModel() {
    val albumList: MutableLiveData<List<Album>> by lazy { MutableLiveData<List<Album>>() }

    fun loadAlbumList() {
        viewModelScope.launch {
            albumList.value = GooglePhotoApi.getAlbumList().filter{it.mediaItemsCount.isNotEmpty()}.toList()
        }
    }
}