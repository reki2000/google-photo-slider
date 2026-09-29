package jp.outlook.rekih.googlephotoslider.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.outlook.rekih.googlephotoslider.data.GooglePhotoApi
import jp.outlook.rekih.googlephotoslider.model.Album
import kotlinx.coroutines.launch

class AlbumSelect : ViewModel() {
    val albumList: MutableLiveData<List<Album>> by lazy { MutableLiveData<List<Album>>() }

    fun loadAlbumList() {
        // Activity 再生成時に再取得しない
        if (albumList.value != null) return
        viewModelScope.launch {
            try {
                albumList.value = GooglePhotoApi.getAlbumList().filter{it.mediaItemsCount.isNotEmpty()}.toList()
            } catch (e: Exception) {
                // todo: エラー表示
                Log.e("AlbumSelect", "failed to load album list", e)
            }
        }
    }
}