package matt.media.player

abstract class TabController
{
    lateinit var rootController: Controller
    
    abstract fun init()
    
    /**
     * Called when the Node associated with the controller is show
     */
    open fun onSelected() {}
}