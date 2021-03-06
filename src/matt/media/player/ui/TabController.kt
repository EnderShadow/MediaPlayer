package matt.media.player.ui

abstract class TabController
{
    lateinit var rootController: Controller
    
    abstract fun init()
    
    /**
     * Called when the Node associated with the controller is shown
     */
    open fun onSelected() {}
}